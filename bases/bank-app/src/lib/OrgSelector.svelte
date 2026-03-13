<script>
  let { organizations, selectedOrgId, onSelect } = $props();
</script>

<div class="org-selector">
  <label for="org-select">Organization</label>
  <select
    id="org-select"
    value={selectedOrgId}
    onchange={(e) => onSelect(e.target.value)}
  >
    {#each organizations as org}
      <option value={org["organization-id"]}>{org.name}</option>
    {/each}
  </select>
</div>

<style>
  .org-selector {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    margin-bottom: 1.5rem;
  }

  label {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--text-secondary);
  }

  select {
    padding: 0.4rem 0.6rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.85rem;
    background: var(--bg-input);
    color: var(--text);
    min-width: 200px;
  }
</style>
