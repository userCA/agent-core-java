import React from 'react';

export const AIAvatar: React.FC<{ size?: number }> = ({ size = 32 }) => (
  <div
    className="rounded-full bg-cream-card border-2 border-ink flex items-center justify-center flex-shrink-0 shadow-card"
    style={{ width: size, height: size }}
  >
    <svg width={size * 0.5} height={size * 0.5} viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3c.132 0 .263 0 .393 0a7.5 7.5 0 0 0 7.92 12.446a9 9 0 1 1 -8.313 -12.454z"/>
      <path d="M12 8v4"/>
      <path d="M12 16h.01"/>
    </svg>
  </div>
);

/* ─── Mitu (咪兔) Square-style Pet Avatar ───
   Reference: cute square-animal doodle style
   ─── */
export const PetAvatar: React.FC<{ size?: number; className?: string }> = ({ size = 24, className = '' }) => {
  const s = size;
  const faceColor = '#f4a8a8';
  const earColor = '#f4b89a';
  const ink = '#3d2b1f';
  const blushColor = '#e89080';
  const pad = s * 0.12;        // padding from edge
  const faceSize = s - pad * 2; // face square size
  const r = faceSize * 0.22;    // corner radius for rounded square
  const cx = s / 2;
  const cy = s / 2 + pad * 0.3; // slightly lower center

  return (
    <div
      className={`rounded-full border-2 border-ink flex items-center justify-center flex-shrink-0 ${className}`}
      style={{ width: size, height: size, background: '#f9d5c0' }}
    >
      <svg width={size} height={size} viewBox={`0 0 ${s} ${s}`}>
        {/* Left ear */}
        <ellipse
          cx={cx - faceSize * 0.28}
          cy={pad + faceSize * 0.12}
          rx={faceSize * 0.12}
          ry={faceSize * 0.22}
          fill={earColor}
          stroke={ink}
          strokeWidth={s * 0.06}
          strokeLinecap="round"
        />
        {/* Right ear */}
        <ellipse
          cx={cx + faceSize * 0.28}
          cy={pad + faceSize * 0.12}
          rx={faceSize * 0.12}
          ry={faceSize * 0.22}
          fill={earColor}
          stroke={ink}
          strokeWidth={s * 0.06}
          strokeLinecap="round"
        />

        {/* Face (rounded square) */}
        <rect
          x={pad}
          y={pad + faceSize * 0.15}
          width={faceSize}
          height={faceSize}
          rx={r}
          ry={r}
          fill={faceColor}
          stroke={ink}
          strokeWidth={s * 0.06}
        />

        {/* Inner ear highlight - left */}
        <ellipse
          cx={cx - faceSize * 0.28}
          cy={pad + faceSize * 0.18}
          rx={faceSize * 0.05}
          ry={faceSize * 0.1}
          fill={faceColor}
          opacity={0.6}
        />
        {/* Inner ear highlight - right */}
        <ellipse
          cx={cx + faceSize * 0.28}
          cy={pad + faceSize * 0.18}
          rx={faceSize * 0.05}
          ry={faceSize * 0.1}
          fill={faceColor}
          opacity={0.6}
        />

        {/* Left eye */}
        <circle
          cx={cx - faceSize * 0.22}
          cy={cy - faceSize * 0.06}
          r={s * 0.055}
          fill={ink}
        />
        {/* Right eye */}
        <circle
          cx={cx + faceSize * 0.22}
          cy={cy - faceSize * 0.06}
          r={s * 0.055}
          fill={ink}
        />
        {/* Eye shine - left */}
        <circle
          cx={cx - faceSize * 0.2}
          cy={cy - faceSize * 0.1}
          r={s * 0.018}
          fill="white"
        />
        {/* Eye shine - right */}
        <circle
          cx={cx + faceSize * 0.24}
          cy={cy - faceSize * 0.1}
          r={s * 0.018}
          fill="white"
        />

        {/* Blush - left */}
        <ellipse
          cx={cx - faceSize * 0.32}
          cy={cy + faceSize * 0.08}
          rx={s * 0.055}
          ry={s * 0.035}
          fill={blushColor}
          opacity={0.5}
        />
        {/* Blush - right */}
        <ellipse
          cx={cx + faceSize * 0.32}
          cy={cy + faceSize * 0.08}
          rx={s * 0.055}
          ry={s * 0.035}
          fill={blushColor}
          opacity={0.5}
        />

        {/* Mouth (small w) */}
        <path
          d={`M ${cx - s * 0.04} ${cy + faceSize * 0.14} Q ${cx} ${cy + faceSize * 0.2} ${cx + s * 0.04} ${cy + faceSize * 0.14}`}
          fill="none"
          stroke={ink}
          strokeWidth={s * 0.05}
          strokeLinecap="round"
        />

        {/* Whiskers - left */}
        <line x1={cx - faceSize * 0.42} y1={cy + faceSize * 0.02} x2={cx - faceSize * 0.3} y2={cy + faceSize * 0.04} stroke={ink} strokeWidth={s * 0.035} strokeLinecap="round" opacity={0.4} />
        <line x1={cx - faceSize * 0.42} y1={cy + faceSize * 0.1} x2={cx - faceSize * 0.3} y2={cy + faceSize * 0.08} stroke={ink} strokeWidth={s * 0.035} strokeLinecap="round" opacity={0.4} />
        {/* Whiskers - right */}
        <line x1={cx + faceSize * 0.42} y1={cy + faceSize * 0.02} x2={cx + faceSize * 0.3} y2={cy + faceSize * 0.04} stroke={ink} strokeWidth={s * 0.035} strokeLinecap="round" opacity={0.4} />
        <line x1={cx + faceSize * 0.42} y1={cy + faceSize * 0.1} x2={cx + faceSize * 0.3} y2={cy + faceSize * 0.08} stroke={ink} strokeWidth={s * 0.035} strokeLinecap="round" opacity={0.4} />
      </svg>
    </div>
  );
};

export const StatusIcon: React.FC<{ status: 'pending' | 'running' | 'complete' | 'error'; size?: number }> = ({ status, size = 16 }) => {
  const colors = {
    pending: 'transparent',
    running: '#a8c8e8',
    complete: '#a8c4a0',
    error: '#e89080',
  };
  const borders = {
    pending: '2px solid #3d2b1f',
    running: '2px solid #3d2b1f',
    complete: '2px solid #3d2b1f',
    error: '2px solid #3d2b1f',
  };

  return (
    <div
      className="rounded-full flex items-center justify-center flex-shrink-0 relative"
      style={{
        width: size,
        height: size,
        background: colors[status],
        border: borders[status],
      }}
    >
      {status === 'running' && (
        <div className="w-2 h-2 border-2 border-ink border-t-transparent rounded-full animate-spin" />
      )}
      {status === 'complete' && (
        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
      )}
      {status === 'error' && (
        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      )}
    </div>
  );
};

export const ToolIcon: React.FC<{ icon: string; bg?: string; size?: number }> = ({ icon, bg = '#f5ecd0', size = 24 }) => {
  const iconSvg = () => {
    switch (icon) {
      case 'search':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>;
      case 'git-compare':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><path d="M11 18H8a2 2 0 0 1-2-2V9"/></svg>;
      case 'play':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polygon points="5 3 19 12 5 21 5 3"/></svg>;
      case 'wand-2':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72"/><path d="m14 7 3 3"/><path d="M5 6v4"/><path d="M19 14v4"/><path d="M10 2v2"/><path d="M7 8H3"/><path d="M21 16h-4"/><path d="M11 3H9"/></svg>;
      case 'bug':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="m8 2 1.88 1.88"/><path d="M14.12 3.88 16 2"/><path d="M9 7.13v-1a3.003 3.003 0 1 1 6 0v1"/><path d="M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6"/><path d="M12 20v-9"/><path d="M6.53 9C4.6 8.8 3 7.1 3 5"/><path d="M6 13H2"/><path d="M3 21c0-2.1 1.7-3.9 3.8-4"/><path d="M20.97 5c0 2.1-1.6 3.8-3.5 4"/><path d="M22 13h-4"/><path d="M17.2 17c2.1.1 3.8 1.9 3.8 4"/></svg>;
      case 'file-text':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/></svg>;
      case 'file-plus':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M9 15h6"/><path d="M12 18v-6"/></svg>;
      case 'file-minus':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M9 15h6"/></svg>;
      case 'file-edit':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10.42 12.61 8.5 15.5l2.89-1.92a1 1 0 0 1 .44-.22l1.36-.36a1 1 0 0 0 .44-.22L18 7.5l-1.5-1.5-4.54 4.54a1 1 0 0 1-.22.44l-.36 1.36a1 1 0 0 1-.22.44z"/></svg>;
      case 'settings':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>;
      case 'book-open':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>;
      case 'arrow-left':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>;
      case 'plus':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>;
      case 'arrow-up':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#fdf6e3" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>;
      case 'x':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>;
      case 'chevron-down':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="6 9 12 15 18 9"/></svg>;
      case 'check-circle':
        return <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#a8c4a0" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>;
      case 'alert-triangle':
        return <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#e89080" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>;
      case 'copy':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#f9d5c0" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>;
      case 'eye':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg>;
      case 'message-square':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>;
      case 'shield-check':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>;
      case 'heart':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="#f4a8a8" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/></svg>;
      case 'activity':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#a8c4a0" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>;
      case 'lightbulb':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="#f9d5c0" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5"/><path d="M9 18h6"/><path d="M10 22h4"/></svg>;
      case 'sparkles':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="#f9d5c0" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/></svg>;
      default:
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/></svg>;
    }
  };

  return (
    <div
      className="rounded-button flex items-center justify-center"
      style={{ width: size, height: size, background: bg, border: '2px solid #3d2b1f' }}
    >
      {iconSvg()}
    </div>
  );
};
