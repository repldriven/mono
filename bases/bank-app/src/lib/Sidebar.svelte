<script>
  import { onMount } from "svelte";

  let { currentPage, onNavigate } = $props();

  const navItems = [
    { id: "organizations", label: "Organizations" },
    { id: "parties", label: "Parties" },
    { id: "accounts", label: "Accounts" },
    { id: "products", label: "Products" },
    { id: "api-keys", label: "API Keys" },
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
    display: block;
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
