import type { PropsWithChildren, ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

export function AdminShell({
  title,
  nav,
  actions,
  children
}: PropsWithChildren<{ title: string; nav: Array<{ to: string; label: string }>; actions?: ReactNode }>) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '240px 1fr', minHeight: '100vh', fontFamily: 'sans-serif' }}>
      <aside style={{ padding: 24, background: '#111827', color: '#fff' }}>
        <h1 style={{ marginTop: 0 }}>{title}</h1>
        {actions ? <div style={{ marginBottom: 24 }}>{actions}</div> : null}
        <nav style={{ display: 'grid', gap: 12 }}>
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              style={({ isActive }) => ({
                color: '#fff',
                textDecoration: 'none',
                opacity: isActive ? 1 : 0.72,
                fontWeight: isActive ? 700 : 400
              })}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main style={{ padding: 24, background: '#f3f4f6' }}>{children}</main>
    </div>
  );
}

export function StatCard({ title, value, description }: { title: string; value: string; description: string }) {
  return (
    <section style={{ background: '#fff', borderRadius: 16, padding: 16, boxShadow: '0 8px 30px rgba(0,0,0,0.06)' }}>
      <div style={{ color: '#6b7280', marginBottom: 8 }}>{title}</div>
      <div style={{ fontSize: 28, fontWeight: 700, marginBottom: 8 }}>{value}</div>
      <div style={{ color: '#4b5563' }}>{description}</div>
    </section>
  );
}

export function SectionHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
      <h2 style={{ margin: 0 }}>{title}</h2>
      {actions}
    </header>
  );
}
