import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter, Routes, Route } from 'react-router'
import './index.css'
import App from './App.tsx'
import Playground from './pages/Playground.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HashRouter>
      <Routes>
        <Route path="/" element={<div className="app-stage"><App /></div>} />
        <Route path="/playground" element={<Playground />} />
      </Routes>
    </HashRouter>
  </StrictMode>,
)
