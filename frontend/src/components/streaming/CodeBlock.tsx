import React, { useState } from 'react';

interface CodeBlockProps {
  language?: string;
  code?: string;
  isStreaming?: boolean;
  bare?: boolean;
}

const DEFAULT_CODE = `public class AuthProvider {
    private final TokenManager tokenManager;

    public AuthResult authenticate(Credentials creds) {
        if (creds == null || !creds.isValid()) {
            throw new AuthException("无效的凭证");
        }
        String token = tokenManager.issueToken(creds);
        return new AuthResult(token);
    }
}`;

const KEYWORDS = new Set([
  'public', 'private', 'protected', 'class', 'interface', 'enum', 'new', 'return',
  'if', 'else', 'for', 'while', 'throw', 'throws', 'try', 'catch', 'finally',
  'import', 'package', 'void', 'final', 'static', 'extends', 'implements',
  'null', 'true', 'false', 'const', 'let', 'var', 'function', 'async', 'await',
]);

const esc = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/* Lightweight, partial-safe tokenizer → token-* spans (see index.css). */
function highlight(code: string): string {
  let out = '';
  let i = 0;
  const n = code.length;
  const wrap = (cls: string, text: string) => `<span class="${cls}">${esc(text)}</span>`;

  while (i < n) {
    const rest = code.slice(i);

    // line / block comment (block may be unterminated while streaming)
    let m = /^\/\/[^\n]*/.exec(rest) || /^\/\*[\s\S]*?(?:\*\/|$)/.exec(rest);
    if (m) { out += wrap('token-comment', m[0]); i += m[0].length; continue; }

    // strings (allow unterminated trailing quote during streaming)
    m = /^"(?:[^"\\]|\\.)*("|$)/.exec(rest) || /^'(?:[^'\\]|\\.)*('|$)/.exec(rest);
    if (m) { out += wrap('token-string', m[0]); i += m[0].length; continue; }

    // numbers
    m = /^\d+(?:\.\d+)?/.exec(rest);
    if (m) { out += wrap('token-number', m[0]); i += m[0].length; continue; }

    // identifiers / keywords / function calls
    m = /^[A-Za-z_]\w*/.exec(rest);
    if (m) {
      const word = m[0];
      if (KEYWORDS.has(word)) out += wrap('token-keyword', word);
      else if (code[i + word.length] === '(') out += wrap('token-function', word);
      else out += esc(word);
      i += word.length;
      continue;
    }

    const ch = code[i];
    if ('{}()[];,.'.includes(ch)) out += wrap('token-punctuation', ch);
    else if ('=+-*/<>!&|%?:'.includes(ch)) out += wrap('token-operator', ch);
    else out += esc(ch);
    i += 1;
  }
  return out;
}

const CodeBlock: React.FC<CodeBlockProps> = ({
  language = 'java',
  code = DEFAULT_CODE,
  isStreaming = false,
  bare = false,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard?.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="code-block animate-slide-down" style={{ maxWidth: bare ? '100%' : '90%', marginTop: bare ? 0 : '8px', boxShadow: bare ? 'none' : undefined }}>
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b-[1.5px] border-ink-faint" style={{ background: 'rgba(255,255,255,0.06)' }}>
        <span className="text-[10px] font-comic uppercase tracking-wider" style={{ color: '#f9d5c0' }}>{language}</span>
        <div className="flex gap-1.5">
          <button
            onClick={handleCopy}
            aria-label="复制代码"
            className="w-8 h-8 rounded-md flex items-center justify-center transition-all duration-150 hover:bg-white/10 cursor-pointer"
            style={{ border: '1px solid rgba(255,255,255,0.12)', color: '#f9d5c0' }}
          >
            {copied ? (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#a8c4a0" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            ) : (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect width="14" height="14" x="8" y="8" rx="2" ry="2" /><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
              </svg>
            )}
          </button>
        </div>
      </div>

      {/* Code content */}
      <div className="px-3.5 py-3 overflow-x-auto custom-scroll">
        <pre className="text-[12.5px] font-mono leading-[1.7] whitespace-pre" style={{ color: '#fdf6e3' }}>
          <code>
            <span dangerouslySetInnerHTML={{ __html: highlight(code) }} />
            {isStreaming && (
              <span className="inline-block w-0.5 h-4 bg-accent-pink ml-0.5 align-middle animate-blink" />
            )}
          </code>
        </pre>
      </div>
    </div>
  );
};

export default CodeBlock;
