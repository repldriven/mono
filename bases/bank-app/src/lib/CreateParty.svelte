<script>
  import { create_party } from "./api.mjs";

  let { onCreated } = $props();

  let displayName = $state("Jane Doe");
  let givenName = $state("Jane");
  let middleNames = $state("");
  let familyName = $state("Doe");
  let dateOfBirth = $state("1990-01-15");
  let nationality = $state("GB");
  let niType = $state("NATIONAL_INSURANCE");
  let niValue = $state("TN000001A");
  let niCountry = $state("GBR");
  let result = $state(null);
  let httpStatus = $state(null);
  let submitting = $state(false);

  function dobToInt(dateStr) {
    return parseInt(dateStr.replace(/-/g, ""), 10);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    submitting = true;
    result = null;
    httpStatus = null;
    try {
      const res = await create_party({
        "display-name": displayName,
        "given-name": givenName,
        "middle-names": middleNames || undefined,
        "family-name": familyName,
        "date-of-birth": dobToInt(dateOfBirth),
        "nationality": nationality,
        "national-identifier": {
          "type": niType,
          "value": niValue,
          "issuing-country": niCountry,
        },
      });
      httpStatus = res["http-status"];
      result = res.body;
      if (httpStatus >= 200 && httpStatus < 300) {
        onCreated?.();
      }
    } catch (err) {
      httpStatus = 0;
      result = { status: "ERROR", error: err.message };
    } finally {
      submitting = false;
    }
  }
</script>

<section>
  <details>
  <summary><h2>Create Party</h2></summary>
  <form onsubmit={handleSubmit}>
    <label>
      Display Name
      <input type="text" bind:value={displayName} required />
    </label>

    <label>
      Given Name
      <input type="text" bind:value={givenName} required />
    </label>

    <label>
      Middle Names
      <input type="text" bind:value={middleNames} />
    </label>

    <label>
      Family Name
      <input type="text" bind:value={familyName} required />
    </label>

    <label>
      Date of Birth
      <input type="date" bind:value={dateOfBirth} required />
    </label>

    <label>
      Nationality
      <input type="text" bind:value={nationality} required maxlength="2"
             placeholder="e.g. GB" />
    </label>

    <fieldset>
      <legend>National Identifier</legend>

      <label>
        Type
        <select bind:value={niType} required>
          <option value="NATIONAL_INSURANCE">National Insurance</option>
        </select>
      </label>

      <label>
        Value
        <input type="text" bind:value={niValue} required
               placeholder="e.g. TN000001A" />
      </label>

      <label>
        Issuing Country
        <input type="text" bind:value={niCountry} required maxlength="3"
               placeholder="e.g. GBR" />
      </label>
    </fieldset>

    <button type="submit" disabled={submitting}>
      {submitting ? "Creating..." : "Create Party"}
    </button>
  </form>

  {#if result}
    <div class="result"
         class:success={httpStatus >= 200 && httpStatus < 300}
         class:warning={httpStatus >= 400 && httpStatus < 500}
         class:error={httpStatus >= 500 || httpStatus === 0}>
      <pre>{JSON.stringify(result, null, 2)}</pre>
    </div>
  {/if}
  </details>
</section>

<style>
  section {
    margin-bottom: 2rem;
  }

  details {
    border: 1px solid var(--details-border);
    border-radius: 6px;
    padding: 0.75rem 1rem;
  }

  summary {
    cursor: pointer;
    list-style: none;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  summary::-webkit-details-marker {
    display: none;
  }

  summary::before {
    content: "\25B6";
    font-size: 0.7rem;
    color: var(--text-muted);
    transition: transform 0.15s;
  }

  details[open] > summary::before {
    transform: rotate(90deg);
  }

  details[open] > summary {
    margin-bottom: 1rem;
  }

  h2 {
    margin: 0;
    display: inline;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-weight: 500;
  }

  input, select {
    padding: 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 1rem;
    background: var(--bg-input);
    color: var(--text);
  }

  fieldset {
    border: 1px solid var(--border-input);
    border-radius: 4px;
    padding: 1rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  legend {
    font-weight: 600;
    padding: 0 0.5rem;
  }

  button {
    padding: 0.6rem 1.2rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 1rem;
    cursor: pointer;
  }

  button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .result {
    margin-top: 1.5rem;
    padding: 1rem;
    border-radius: 4px;
  }

  .success {
    background: #dcfce7;
    border: 1px solid #86efac;
    color: #166534;
  }

  .warning {
    background: #fef9c3;
    border: 1px solid #fde047;
    color: #854d0e;
  }

  .error {
    background: #fee2e2;
    border: 1px solid #fca5a5;
    color: #991b1b;
  }

  pre {
    margin-top: 0.5rem;
    font-size: 0.85rem;
    white-space: pre-wrap;
    word-break: break-all;
  }
</style>
