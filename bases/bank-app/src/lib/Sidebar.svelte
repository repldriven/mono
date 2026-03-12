<script>
  let { currentPage, onNavigate } = $props();

  const navItems = [
    { id: "organizations", label: "Organizations" },
    { id: "parties", label: "Parties" },
    { id: "accounts", label: "Accounts" },
  ];
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
</style>
