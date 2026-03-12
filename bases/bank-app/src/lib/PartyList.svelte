<script>
  import { list_parties, open_account } from "./api.mjs";
  import { timeAgo } from "./time.mjs";
  import { onMount } from "svelte";

  let { onAccountOpened } = $props();

  let parties = $state([]);
  let links = $state({});
  let loading = $state(false);
  let error = $state(null);
  let currentQuery = $state(null);

  function queryFromLink(url) {
    const idx = url.indexOf("?");
    return idx >= 0 ? url.substring(idx + 1) : null;
  }

  export async function load(queryString) {
    loading = true;
    error = null;
    currentQuery = queryString ?? null;
    try {
      const res = await list_parties(queryString);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        parties = res.body.parties ?? [];
        links = res.body.links ?? {};
      } else {
        error = res.body?.error ?? `HTTP ${res["http-status"]}`;
        parties = [];
        links = {};
      }
    } catch (err) {
      error = err.message;
      parties = [];
      links = {};
    } finally {
      loading = false;
    }
  }

  let opening = $state({});

  async function handleOpenAccount(party) {
    const partyId = party["party-id"];
    opening[partyId] = true;
    try {
      await open_account({
        "party-id": partyId,
        "name": party["display-name"],
        "currency": "GBP",
      });
      await load(currentQuery);
      onAccountOpened?.();
    } finally {
      delete opening[partyId];
    }
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>Parties</h2>
    <button class="refresh" onclick={() => load(currentQuery)} disabled={loading}>
      {loading ? "Loading..." : "Refresh"}
    </button>
  </div>

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>Org ID</th>
        <th>Party ID</th>
        <th>Display Name</th>
        <th>Status</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if parties.length === 0 && !loading}
        <tr><td colspan="7" class="empty">No parties found</td></tr>
      {/if}
      {#each parties as party}
        <tr>
          <td class="mono">{party["organization-id"]}</td>
          <td class="mono">{party["party-id"]}</td>
          <td>{party["display-name"]}</td>
          <td>{party.status}</td>
          <td title={party["created-at"]}>{timeAgo(party["created-at"])}</td>
          <td title={party["updated-at"]}>{timeAgo(party["updated-at"])}</td>
          <td>
            {#if party.status === "active"}
              <button
                class="action-btn"
                disabled={opening[party["party-id"]]}
                onclick={() => handleOpenAccount(party)}
              >
                {opening[party["party-id"]] ? "Opening..." : "Open Account"}
              </button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="pagination">
    <button
      disabled={!links.prev || loading}
      onclick={() => load(queryFromLink(links.prev))}
    >
      Prev
    </button>
    <button
      disabled={!links.next || loading}
      onclick={() => load(queryFromLink(links.next))}
    >
      Next
    </button>
  </div>
</section>

<style>
  section {
    margin-top: 2rem;
  }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
  }

  h2 {
    margin: 0;
  }

  .refresh {
    padding: 0.4rem 0.8rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .refresh:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .error-msg {
    background: #fee2e2;
    border: 1px solid #fca5a5;
    padding: 0.75rem;
    border-radius: 4px;
    margin-bottom: 1rem;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
  }

  th, td {
    text-align: left;
    padding: 0.5rem 0.6rem;
    border-bottom: 1px solid #e5e7eb;
  }

  th {
    background: #f9fafb;
    font-weight: 600;
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: #6b7280;
  }

  .mono {
    font-family: monospace;
    font-size: 0.8rem;
  }

  .empty {
    text-align: center;
    color: #9ca3af;
    padding: 1.5rem;
  }

  .pagination {
    display: flex;
    justify-content: center;
    gap: 0.5rem;
    margin-top: 1rem;
  }

  .pagination button {
    padding: 0.4rem 1rem;
    background: #f3f4f6;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.85rem;
  }

  .pagination button:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .pagination button:not(:disabled):hover {
    background: #e5e7eb;
  }

  .action-btn {
    padding: 0.25rem 0.6rem;
    background: #16a34a;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
  }

  .action-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .action-btn:not(:disabled):hover {
    background: #15803d;
  }
</style>
