(ns com.repldriven.mono.realworld-store.users
  (:require
    [com.repldriven.mono.realworld-store.sql :as sql]

    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.jdbc.interface :as jdbc]))

(def ^:private columns
  (str "id, username, email, password_hash, bio, image, "
       (sql/at "created_at")
       " as created_at, "
       (sql/at "updated_at")
       " as updated_at"))

(defn by-id
  [ds id]
  (jdbc/execute-one! ds
                     [(str "select " columns " from users where id = ?") id]))

(defn by-email
  [ds email]
  (jdbc/execute-one! ds
                     [(str "select " columns " from users where email = ?")
                      email]))

(defn by-username
  [ds username]
  (jdbc/execute-one! ds
                     [(str "select " columns " from users where username = ?")
                      username]))

(defn- taken
  "Which of username or email already belongs to someone else.

  Asked before inserting rather than inferred from a constraint violation,
  because the API has to name the offending field and a 23505 anomaly does
  not carry the constraint name — `jdbc`'s payload has the SQLSTATE and the
  vendor code and nothing else. The unique indexes remain the backstop for
  the race between this check and the insert."
  [ds username email exclude-id]
  (let-nom>
    [rows (jdbc/execute! ds
                         [(str "select username, email from users"
                               " where (username = ?::text"
                               "     or email = ?::text)"
                               "   and (?::bigint is null"
                               "     or id <> ?::bigint)")
                          username email exclude-id exclude-id])]
    (cond (some #(= username (:username %)) rows)
          :realworld/username-taken

          (some #(= email (:email %)) rows)
          :realworld/email-taken

          :else
          nil)))

(defn create
  "Register a user. Returns the row, or a rejection naming the taken field."
  [ds data]
  (let [{:keys [username email password]} data]
    (jdbc/with-transaction
     [tx ds nil]
     (let-nom>
       [clash (taken tx username email nil)
        _ (if clash (error/reject clash "already taken") nil)
        hashed (auth/hash-password password)
        row (jdbc/execute-one!
             tx
             [(str "insert into users (username, email, password_hash)"
                   " values (?, ?, ?) returning "
                   columns)
              username email hashed])]
       row))))

(defn authenticate
  "The user for these credentials, or a `credentials-invalid` rejection.

  The same rejection whether the email is unknown or the password is wrong:
  telling them apart tells an attacker which emails are registered."
  [ds email password]
  (let-nom>
    [row (by-email ds email)]
    (if (and row (auth/verify-password password (:password-hash row)))
      row
      (error/reject :realworld/credentials-invalid "invalid"))))

(defn update-user
  "Patch the fields present in `data`. Absent keys are left alone, which is
  why the caller must pass only what was in the request body."
  [ds id data]
  (let [{:keys [username email password bio image]} data
        set-username (contains? data :username)
        set-email (contains? data :email)
        set-password (contains? data :password)
        set-bio (contains? data :bio)
        set-image (contains? data :image)]
    (jdbc/with-transaction
     [tx ds nil]
     (let-nom>
       [clash (taken tx
                     (when set-username username)
                     (when set-email email)
                     id)
        _ (if clash (error/reject clash "already taken") nil)
        hashed (if set-password (auth/hash-password password) nil)
        row (jdbc/execute-one!
             tx
             [(str "update users set"
                   "   username = coalesce(?::text, username),"
                   "   email = coalesce(?::text, email),"
                   "   password_hash = coalesce(?::text, password_hash),"
                   "   bio = case when ?::boolean then ?::text else bio end,"
                   "   image ="
                   "     case when ?::boolean then ?::text else image end,"
                   "   updated_at = now()"
                   " where id = ?::bigint returning "
                   columns)
              (when set-username username) (when set-email email) hashed
              set-bio bio set-image image id])]
       row))))

(defn token
  [signer row]
  (auth/sign-token signer {:sub (str (:id row))}))
