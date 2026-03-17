<script>
  import { onMount } from "svelte";

  let { currentPage, onNavigate } = $props();

  const navItems = [
    { id: "organizations", label: "Organizations", icon: `<svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M3 1a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1H3zm1 2h2v2H4V3zm0 4h2v2H4V7zm0 4h2v2H4v-2zm4-8h2v2H8V3zm0 4h2v2H8V7zm0 4h2v2H8v-2z"/></svg>` },
    { id: "products", label: "Products", icon: `<svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3zm0 5a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V8zm1 4a1 1 0 0 0-1 1v1a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-1a1 1 0 0 0-1-1H3z"/></svg>` },
    { id: "parties", label: "Parties", icon: `<svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M8 1a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM2 13c0-3 2.5-5 6-5s6 2 6 5a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1z"/></svg>` },
    { id: "accounts", label: "Accounts", icon: `<svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M2 4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1H2V4zm0 3v5a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H2zm3 2h2a1 1 0 0 1 0 2H5a1 1 0 0 1 0-2z"/></svg>` },
    { id: "api-keys", label: "API Keys", icon: `<svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M10.5 1a4.5 4.5 0 0 0-4.38 5.57L2 10.7V14a1 1 0 0 0 1 1h2v-2h2v-2h1.59l.53-.53A4.5 4.5 0 1 0 10.5 1zm1 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z"/></svg>` },
  ];

  let dark = $state(false);

  onMount(() => {
    dark = localStorage.getItem("theme") === "dark"
           || (!localStorage.getItem("theme")
               && window.matchMedia("(prefers-color-scheme: dark)").matches);
    applyTheme();
  });

  function applyTheme() {
    document.documentElement.classList.toggle("dark", dark);
  }

  function toggleTheme() {
    dark = !dark;
    localStorage.setItem("theme", dark ? "dark" : "light");
    applyTheme();
  }
</script>

<nav>
  <div class="brand">Queenswood</div>
  {#each navItems as item}
    <button
      class="nav-item"
      class:active={currentPage === item.id}
      onclick={() => onNavigate(item.id)}
    >
      <span class="nav-icon">{@html item.icon}</span>
      {item.label}
    </button>
  {/each}
  <div class="spacer"></div>
  <button class="theme-toggle" onclick={toggleTheme} title={dark ? "Switch to light mode" : "Switch to dark mode"}>
    <span class="toggle-track" class:dark>
      <span class="toggle-icon sun">&#9788;</span>
      <span class="toggle-thumb" class:dark></span>
      <span class="toggle-icon moon">&#9790;</span>
    </span>
  </button>
</nav>

<style>
  nav {
    width: 220px;
    min-width: 220px;
    height: 100%;
    background: #1e293b;
    display: flex;
    flex-direction: column;
    padding-top: 1rem;
  }

  .brand {
    color: white;
    font-size: 1.2rem;
    font-weight: 700;
    padding: 0.75rem 1.25rem 1.5rem;
    letter-spacing: 0.02em;
  }

  .nav-item {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    width: 100%;
    text-align: left;
    padding: 0.65rem 1.25rem;
    background: none;
    border: none;
    border-left: 3px solid transparent;
    color: #94a3b8;
    font-size: 0.9rem;
    cursor: pointer;
    transition: background 0.15s, color 0.15s;
  }

  .nav-icon {
    display: flex;
    align-items: center;
    flex-shrink: 0;
  }

  .nav-item:hover {
    background: #334155;
    color: #e2e8f0;
  }

  .nav-item.active {
    border-left-color: #2563eb;
    background: #334155;
    color: white;
    font-weight: 600;
  }

  .spacer {
    flex: 1;
  }

  .theme-toggle {
    display: flex;
    justify-content: center;
    padding: 1rem 1.25rem;
    background: none;
    border: none;
    cursor: pointer;
  }

  .toggle-track {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 48px;
    height: 24px;
    background: #475569;
    border-radius: 12px;
    padding: 0 4px;
    transition: background 0.2s;
  }

  .toggle-track.dark {
    background: #1e40af;
  }

  .toggle-thumb {
    position: absolute;
    left: 3px;
    width: 18px;
    height: 18px;
    background: white;
    border-radius: 50%;
    transition: transform 0.2s;
  }

  .toggle-thumb.dark {
    transform: translateX(24px);
  }

  .toggle-icon {
    font-size: 0.75rem;
    z-index: 1;
    line-height: 1;
  }

  .toggle-icon.sun {
    color: #fbbf24;
  }

  .toggle-icon.moon {
    color: #e2e8f0;
  }
</style>
