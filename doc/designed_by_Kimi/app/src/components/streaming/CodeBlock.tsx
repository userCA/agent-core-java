import React, { useState } from 'react';

interface CodeBlockProps {
  language?: string;
  code?: string;
  isStreaming?: boolean;
}

const CodeBlock: React.FC<CodeBlockProps> = ({
  language = 'java',
  code = `public class AuthProvider {
    private TokenManager tokenManager;
    
    public AuthResult authenticate(Credentials creds) {
        // Validate credentials
        if (creds == null || creds.isEmpty()) {
            throw new IllegalArgumentException("Credentials required");
        }
        
        // Check token
        String token = tokenManager.issueToken(creds);
        return new AuthResult(token);
    }
}`,
  isStreaming = false,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard?.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="code-block animate-slide-down" style={{ maxWidth: '90%', marginTop: '8px' }}>
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b-[1.5px] border-ink-faint" style={{ background: 'rgba(255,255,255,0.06)' }}>
        <span className="text-[10px] font-comic uppercase tracking-wider" style={{ color: '#f9d5c0' }}>{language}</span>
        <div className="flex gap-1.5">
          <button
            onClick={handleCopy}
            className="w-6 h-6 rounded-md flex items-center justify-center transition-all duration-150 hover:bg-white/10"
            style={{ border: '1px solid rgba(255,255,255,0.12)', color: '#f9d5c0' }}
          >
            {copied ? (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#a8c4a0" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            ) : (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>
              </svg>
            )}
          </button>
        </div>
      </div>

      {/* Code content */}
      <div className="px-3.5 py-3 overflow-x-auto custom-scroll">
        <pre className="text-[12.5px] font-mono leading-[1.7] whitespace-pre" style={{ color: '#fdf6e3' }}>
          <code>
            <span className="token-keyword">public class</span> <span className="token-function">AuthProvider</span> <span className="token-punctuation">{"{"}</span>{"\n"}
            {"    "}<span className="token-keyword">private</span> TokenManager tokenManager{";\n"}
            {"    "}{"\n"}
            {"    "}<span className="token-keyword">public</span> AuthResult <span className="token-function">authenticate</span><span className="token-punctuation">(</span>Credentials creds<span className="token-punctuation">)</span> <span className="token-punctuation">{"{"}</span>{"\n"}
            {"        "}<span className="token-comment">// Validate credentials</span>{"\n"}
            {"        "}<span className="token-keyword">if</span> <span className="token-punctuation">(</span>creds <span className="token-operator">==</span> <span className="token-keyword">null</span> <span className="token-operator">||</span> creds.isEmpty<span className="token-punctuation">()</span><span className="token-punctuation">)</span> <span className="token-punctuation">{"{"}</span>{"\n"}
            {"            "}<span className="token-keyword">throw new</span> <span className="token-function">IllegalArgumentException</span><span className="token-punctuation">(</span><span className="token-string">&quot;Credentials required&quot;</span><span className="token-punctuation">)</span>{";\n"}
            {"        "}<span className="token-punctuation">{"}"}</span>{"\n"}
            {"        "}{"\n"}
            {"        "}<span className="token-comment">// Check token</span>{"\n"}
            {"        "}String token <span className="token-operator">=</span> tokenManager.<span className="token-function">issueToken</span><span className="token-punctuation">(</span>creds<span className="token-punctuation">)</span>{";\n"}
            {"        "}<span className="token-keyword">return new</span> <span className="token-function">AuthResult</span><span className="token-punctuation">(</span>token<span className="token-punctuation">)</span>{";\n"}
            {"    "}<span className="token-punctuation">{"}"}</span>{"\n"}
            <span className="token-punctuation">{"}"}</span>
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
