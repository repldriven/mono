(ns com.repldriven.mono.bank-interest.domain)

(def ^:private micro-scale 1000000)

(defn- net-balance
  "Returns credit minus debit for a balance."
  [balance]
  (- (:credit balance 0) (:debit balance 0)))

(defn daily-interest
  "Calculates daily interest using integer micro-unit
  arithmetic. Returns {:whole-units n :carry c} where
  carry is in micro-minor-units (1 minor unit = 1e6
  micro). existing-carry is the previous day's carry."
  [balance interest-rate-bps existing-carry]
  (let [net (net-balance balance)
        total-micro (+ (* net
                          interest-rate-bps
                          (quot micro-scale 10000))
                       (* existing-carry 365))
        daily-micro (quot total-micro 365)
        whole-units (quot daily-micro micro-scale)
        new-carry (rem daily-micro micro-scale)]
    {:whole-units whole-units :carry new-carry}))

(defn accrual-idempotency-key
  [account-id as-of-date]
  (str "accrue-" account-id "-" as-of-date))

(defn capitalization-idempotency-key
  [account-id as-of-date]
  (str "capitalize-" account-id "-" as-of-date))

(defn accrual-transaction
  "Builds the transaction data for a daily interest
  accrual. Returns nil when whole-units is zero."
  [settlement-id account-id currency whole-units
   as-of-date]
  (when-not (zero? whole-units)
    {:idempotency-key (accrual-idempotency-key
                       account-id
                       as-of-date)
     :transaction-type :transaction-type-interest-accrual
     :currency currency
     :reference (str "Daily interest accrual " as-of-date)
     :legs [;; debit  → org settlement interest-payable/posted
            ;; credit → customer interest-accrued/posted
            {:account-id settlement-id
             :balance-type :balance-type-interest-payable
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount whole-units}
            {:account-id account-id
             :balance-type :balance-type-interest-accrued
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount whole-units}]}))

(defn capitalization-transaction
  "Builds the transaction data for monthly interest
  capitalization. Returns nil when accrued is zero."
  [settlement-id account-id currency balance as-of-date]
  (let [accrued (net-balance balance)]
    (when-not (zero? accrued)
      {:idempotency-key (capitalization-idempotency-key
                         account-id
                         as-of-date)
       :transaction-type
       :transaction-type-interest-capital
       :currency currency
       :reference (str "Monthly interest capitalization "
                       as-of-date)
       :legs [;; debit  → org settlement default/posted
              ;; credit → customer interest-paid/posted
              {:account-id settlement-id
               :balance-type :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount accrued}
              {:account-id account-id
               :balance-type :balance-type-interest-paid
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount accrued}
              ;; debit  → customer interest-accrued/posted credit → org
              ;; credit → settlement interest-payable/posted
              {:account-id account-id
               :balance-type
               :balance-type-interest-accrued
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount accrued}
              {:account-id settlement-id
               :balance-type
               :balance-type-interest-payable
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount accrued}
              ;; debit  → customer interest-paid/posted
              ;; credit → customer default/posted
              {:account-id account-id
               :balance-type
               :balance-type-interest-paid
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount accrued}
              {:account-id account-id
               :balance-type
               :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount accrued}]})))





