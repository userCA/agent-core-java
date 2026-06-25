import React from 'react';

interface SearchResult {
  path: string;
  filename: string;
  preview: string;
  highlights?: string[];
}

interface SearchResultBlockProps {
  title?: string;
  count?: string;
  results?: SearchResult[];
}

const SearchResultBlock: React.FC<SearchResultBlockProps> = ({
  title = 'Code Search',
  count = '3 results',
  results = [
    {
      path: 'src/auth/',
      filename: 'AuthProvider.java',
      preview: 'public class AuthProvider { private TokenManager tokenManager;',
      highlights: ['AuthProvider'],
    },
    {
      path: 'src/auth/',
      filename: 'TokenManager.java',
      preview: 'public class TokenManager { public String issueToken(...) {',
      highlights: ['TokenManager'],
    },
    {
      path: 'src/utils/',
      filename: 'JwtUtil.java',
      preview: 'public class JwtUtil { private static final String SECRET =',
      highlights: ['JwtUtil'],
    },
  ],
}) => {
  const highlightText = (text: string, highlights: string[] = []) => {
    if (highlights.length === 0) return text;
    let result = text;
    highlights.forEach(h => {
      result = result.replace(h, `<span class="diff-highlight">${h}</span>`);
    });
    return <span dangerouslySetInnerHTML={{ __html: result }} />;
  };

  return (
    <div className="streaming-card animate-slide-down">
      {/* Header */}
      <div className="flex items-center gap-2 mb-2.5">
        <div
          className="w-6 h-6 rounded-md flex items-center justify-center"
          style={{ background: '#f5ecd0', border: '1.5px solid #3d2b1f' }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
        </div>
        <span className="text-[13px] font-bold text-ink font-display">{title}</span>
        <span className="ml-auto text-[10px] font-comic text-ink-muted px-2 py-0.5 bg-cream-warm border border-ink rounded-pill">{count}</span>
      </div>

      {/* Divider */}
      <div className="mb-2.5 border-t-[1.5px] border-dashed border-ink-faint" />

      {/* Results */}
      <div className="space-y-1.5">
        {results.map((r, i) => (
          <div
            key={i}
            className="flex flex-col gap-1 p-3 bg-cream-surface border-[1.5px] border-ink rounded-[10px] cursor-pointer transition-all duration-150 hover:bg-cream-warm hover:translate-x-0.5"
          >
            <div className="text-[10px] text-ink-muted font-sans">{r.path}<span className="font-semibold text-ink">{r.filename}</span></div>
            <div className="font-mono text-[11px] text-ink-muted leading-relaxed mt-1 pl-2 border-l-2 border-accent-pink" style={{ background: 'rgba(61,43,31,0.04)', padding: '6px 8px', borderRadius: '0 6px 6px 0' }}>
              {highlightText(r.preview, r.highlights)}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default SearchResultBlock;
