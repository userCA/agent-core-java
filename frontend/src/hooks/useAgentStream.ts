import { useCallback, useRef, useState } from 'react';
import type { AgentTurn, BlockSpec, LiveBlock, RunStatus, Script } from '../lib/stream/types';

/* ───────────────────────────────────────────────
   useAgentStream — drives a Script over a timeline:
   typing → 逐步揭示 block → 逐字答案 → done.
   Mutates a working AgentTurn and re-renders on each
   tick. Honors prefers-reduced-motion and supports stop().
   ─────────────────────────────────────────────── */

const prefersReduced =
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

const IDLE_TURN: AgentTurn = { phase: 'idle', blocks: [], answer: '', answerDone: false };

let blockSeq = 0;

function initLiveBlock(spec: BlockSpec): LiveBlock {
  const subCount = spec.kind === 'skill' ? spec.subTools.length : 0;
  const fileCount = spec.kind === 'file' ? spec.operations.length : 0;
  return {
    id: `b_${++blockSeq}`,
    spec,
    status: 'pending',
    typed: '',
    toolStatus: 'preparing',
    progress: 0,
    subStatus: Array<RunStatus>(subCount).fill('pending'),
    fileStatus: Array<RunStatus>(fileCount).fill('pending'),
  };
}

function snapshot(t: AgentTurn): AgentTurn {
  return {
    phase: t.phase,
    answer: t.answer,
    answerDone: t.answerDone,
    blocks: t.blocks.map((b) => ({ ...b, subStatus: [...b.subStatus], fileStatus: [...b.fileStatus] })),
  };
}

export interface UseAgentStream {
  turn: AgentTurn;
  isStreaming: boolean;
  start: (script: Script, onDone?: (finalTurn: AgentTurn) => void) => void;
  stop: () => void;
}

export function useAgentStream(): UseAgentStream {
  const [turn, setTurn] = useState<AgentTurn>(IDLE_TURN);
  const workRef = useRef<AgentTurn>(IDLE_TURN);
  const signalRef = useRef<{ cancelled: boolean }>({ cancelled: false });

  const commit = useCallback(() => setTurn(snapshot(workRef.current)), []);

  const stop = useCallback(() => {
    signalRef.current.cancelled = true;
    const w = workRef.current;
    w.blocks.forEach((b) => {
      if (b.status === 'running') b.status = 'complete';
      if (b.toolStatus !== 'complete' && b.toolStatus !== 'error') b.toolStatus = 'complete';
    });
    w.phase = 'done';
    w.answerDone = true;
    commit();
  }, [commit]);

  const start = useCallback(
    (script: Script, onDone?: (finalTurn: AgentTurn) => void) => {
      // cancel any in-flight run
      signalRef.current.cancelled = true;
      const signal = { cancelled: false };
      signalRef.current = signal;

      const work: AgentTurn = { phase: 'typing', blocks: [], answer: '', answerDone: false };
      workRef.current = work;
      commit();

      const typeInto = async (set: (v: string) => void, text: string, cps: number) => {
        if (prefersReduced) { set(text); return; }
        const step = Math.max(8, Math.round(1000 / cps));
        for (let i = 1; i <= text.length; i++) {
          if (signal.cancelled) return;
          set(text.slice(0, i));
          await sleep(text[i - 1] === '\n' ? step + 40 : step);
        }
        if (!signal.cancelled) set(text);
      };

      const animate = async (b: LiveBlock) => {
        const spec = b.spec;
        switch (spec.kind) {
          case 'thinking':
            b.status = 'running'; commit();
            await typeInto((v) => { b.typed = v; commit(); }, spec.content, 48);
            b.status = 'complete'; commit();
            break;
          case 'code':
            b.status = 'running'; commit();
            await typeInto((v) => { b.typed = v; commit(); }, spec.code, 90);
            b.status = 'complete'; commit();
            break;
          case 'tool':
            b.status = 'running'; b.toolStatus = 'preparing'; commit();
            await sleep(prefersReduced ? 0 : 350);
            if (signal.cancelled) return;
            b.toolStatus = 'executing'; commit();
            await sleep(prefersReduced ? 0 : 950);
            if (signal.cancelled) return;
            if (spec.errorMessage) { b.toolStatus = 'error'; b.status = 'error'; }
            else { b.toolStatus = 'complete'; b.status = 'complete'; }
            commit();
            break;
          case 'skill': {
            b.status = 'running'; commit();
            const n = spec.subTools.length;
            for (let i = 0; i < n; i++) {
              if (signal.cancelled) return;
              b.subStatus[i] = 'running';
              b.progress = Math.round((i / n) * 100);
              commit();
              await sleep(prefersReduced ? 0 : 800);
              if (signal.cancelled) return;
              b.subStatus[i] = 'complete'; commit();
            }
            b.progress = 100; b.status = 'complete'; commit();
            break;
          }
          case 'file': {
            b.status = 'running'; commit();
            for (let i = 0; i < spec.operations.length; i++) {
              if (signal.cancelled) return;
              b.fileStatus[i] = 'running'; commit();
              await sleep(prefersReduced ? 0 : 450);
              b.fileStatus[i] = 'complete'; commit();
            }
            b.status = 'complete'; commit();
            break;
          }
          case 'search':
          case 'diff':
            b.status = 'running'; commit();
            await sleep(prefersReduced ? 0 : 320);
            b.status = 'complete'; commit();
            break;
        }
      };

      (async () => {
        await sleep(prefersReduced ? 0 : 650); // typing indicator
        if (signal.cancelled) return;
        work.phase = 'streaming'; commit();

        for (const spec of script.steps) {
          if (signal.cancelled) break;
          const block = initLiveBlock(spec);
          work.blocks.push(block);
          commit();
          await animate(block);
          if (signal.cancelled) break;
          await sleep(prefersReduced ? 0 : 160);
        }

        if (!signal.cancelled) {
          await typeInto((v) => { work.answer = v; commit(); }, script.answer, 34);
        }
        work.answerDone = true;
        work.phase = 'done';
        commit();
        if (!signal.cancelled) onDone?.(snapshot(work));
      })();
    },
    [commit],
  );

  const isStreaming = turn.phase === 'typing' || turn.phase === 'streaming';
  return { turn, isStreaming, start, stop };
}
