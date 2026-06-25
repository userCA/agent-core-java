/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        peach: {
          bg: '#f4b89a',
          light: '#f8c4a8',
          soft: '#f9d5c0',
          muted: '#e8a88a',
        },
        cream: {
          bg: '#f9f1d8',
          card: '#fdf6e3',
          surface: '#fffbf0',
          warm: '#f5ecd0',
        },
        ink: {
          DEFAULT: '#3d2b1f',
          muted: '#7a6b5f',
          faint: 'rgba(61,43,31,0.4)',
          ghost: 'rgba(61,43,31,0.15)',
        },
        border: {
          passive: '#e8dcc8',
          interactive: 'rgba(61,43,31,0.4)',
        },
        accent: {
          pink: '#f4a8a8',
          sage: '#a8c4a0',
          sky: '#a8c8e8',
          lavender: '#c8b8d8',
          coral: '#e89080',
        },
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
      },
      fontFamily: {
        sans: ['Inter', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', 'Hiragino Sans GB', 'system-ui', 'sans-serif'],
        display: ['Ma Shan Zheng', 'Noto Sans SC', 'PingFang SC', 'cursive'],
        hand: ['Ma Shan Zheng', 'Noto Sans SC', 'PingFang SC', 'cursive'],
        comic: ['Ma Shan Zheng', 'Noto Sans SC', 'PingFang SC', 'cursive'],
        mono: ['SF Mono', 'Fira Code', 'Cascadia Code', 'monospace'],
      },
      borderRadius: {
        button: '10px',
        card: '16px',
        pill: '9999px',
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
      boxShadow: {
        'dark-btn': '3px 3px 0 #3d2b1f',
        card: '3px 3px 0 #3d2b1f',
        float: '4px 4px 0 #3d2b1f',
        subtle: '1px 1px 0 rgba(61,43,31,0.2)',
        frame: '1px 1px 0 #3d2b1f, 0 25px 80px rgba(61,43,31,0.15)',
      },
      keyframes: {
        'slide-up': {
          '0%': { transform: 'translateY(100%)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        'slide-down': {
          '0%': { transform: 'translateY(-10px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'pop-in': {
          '0%': { transform: 'scale(0.8)', opacity: '0' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        'gentle-bounce': {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-3px)' },
        },
        typing: {
          '0%, 60%, 100%': { transform: 'translateY(0)' },
          '30%': { transform: 'translateY(-4px)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        'pulse-ring': {
          '0%': { transform: 'scale(1)', opacity: '0.5' },
          '100%': { transform: 'scale(1.8)', opacity: '0' },
        },
        'skill-stripe': {
          '0%': { backgroundPosition: '0 0' },
          '100%': { backgroundPosition: '11px 0' },
        },
        blink: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0' },
        },
        spin: {
          '0%': { transform: 'rotate(0deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
      },
      animation: {
        'slide-up': 'slide-up 0.25s ease-out forwards',
        'slide-down': 'slide-down 0.2s ease-out forwards',
        'fade-in': 'fade-in 0.2s ease-out forwards',
        'pop-in': 'pop-in 0.2s ease-out forwards',
        'gentle-bounce': 'gentle-bounce 2s ease-in-out infinite',
        typing: 'typing 1.2s ease-in-out infinite',
        shimmer: 'shimmer 1.5s infinite',
        'pulse-ring': 'pulse-ring 1.5s ease-out infinite',
        'skill-stripe': 'skill-stripe 0.5s linear infinite',
        blink: 'blink 1s step-end infinite',
        spin: 'spin 0.8s linear infinite',
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
}
