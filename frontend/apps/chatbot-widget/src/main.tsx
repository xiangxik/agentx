import React from 'react';
import ReactDOM from 'react-dom/client';

import { App } from './App';

const mountNode = document.getElementById('root');

if (mountNode) {
  ReactDOM.createRoot(mountNode).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}
