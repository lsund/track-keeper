(ns goal-tracker.db.read
  (:require [clojure.java.jdbc :as j]
            [clj-time.core :as time]
            [goal-tracker.util :as util]
            [medley.core :refer [find-first]]
            [goal-tracker.db.core :as db]
            [slingshot.slingshot :refer [throw+]]))

(defn row [db table id]
  (first (j/query db [(str "SELECT * FROM " (name table) " WHERE id=?") id])))

(defn value [db table column id]
  (-> db
      (j/query [(str "SELECT " (name column) " from " (name table) " where id = ?") id])
      first
      column))

(defn all [db table]
  (j/query db [(str "SELECT * FROM " (name table))]))

(defn all-where [db table clause]
  (j/query db [(str "SELECT * FROM " (name table) " WHERE " clause)]))

(defn all-ordered-by
  ([db table col]
   (all-ordered-by db table col :asc))
  ([db table col how]
   (j/query db [(str "SELECT * FROM " (name table) " ORDER BY  " (name col)  " " (name how)) ])))

(defn subgoals [db]
  (j/query db
           ["SELECT subgoal.*, goal.sequence
             FROM subgoal
             INNER JOIN goal
             ON subgoal.goalid = goal.id
             WHERE subgoal.done = 'f'
             ORDER BY deadline"]))

(defn iteration
  ([db]
   (iteration db 0))
  ([db n]
   (let [now (util/->sqldate (.plusMonths (time/now) n))]
     (if-let [iteration (first (j/query db
                                        [(str "SELECT *
                                              FROM iteration
                                              WHERE ? >= startdate and ? <= enddate")
                                         now
                                         now]))]
       iteration
       (throw (Exception. "No iteration could be read"))))))

(defn all-incremental-tasks [db goalid iterationid]
  (j/query db ["SELECT task.*, actionitem.description as actionitemdescription
                FROM task
                INNER JOIN actionitem
                ON actionitem.id = task.actionitemid
                WHERE task.iterationid = ? AND task.goalid = ?"
               iterationid
               goalid]))

(defn done-goal-ids [db iterationid]
  (map :id (j/query db
                    ["SELECT id FROM goal WHERE id NOT IN (
                      (SELECT distinct(goalid)
                       FROM task
                       WHERE current < target AND iterationid = ?));"
                     iterationid])))

(defn task-log [db iterationid goalid]
  (j/query db
           ["SELECT doneTaskEntry.day,
                    doneTaskEntry.taskid,
                    doneTaskEntry.id,
                    actionItem.description as actionitemdescription,
                    task.description as taskdescription
             FROM doneTaskEntry
             INNER JOIN task
             ON doneTaskEntry.taskid = task.id
             INNER JOIN actionItem
             ON task.actionitemid = actionItem.id
             WHERE doneTaskEntry.tasktype = 1
             AND doneTaskEntry.taskid IN
               (SELECT id FROM task WHERE iterationid = ? AND goalid = ?)
             ORDER BY doneTaskEntry.day;"
            iterationid
            goalid]))

(defn- parse-time-val [{:keys [timeestimate target]}]
  (when timeestimate
    [(* (util/extract-int timeestimate) (or target 1))
     (cond
       (re-matches #"[0-9]+h" timeestimate) :hours
       (re-matches #"[0-9]+m" timeestimate) :minutes
       :default nil)]))

(defn total-estimate [db goalid iterationid]
  (->>
   (j/query db
            ["SELECT timeestimate, target
                      FROM task
                      WHERE goalid = ? AND iterationid = ?"
             goalid
             iterationid])
   (map parse-time-val)
   (group-by second)
   (map (fn [[unit estimates]] [unit (apply + (map first estimates))]))
   (into {})
   util/normalize-time))

(defn- map-vals [f xs]
  (map (fn [[k v]] [k (f v)]) xs))

(defn total-estimates [db iterationid]
  (->>
   (j/query db
            ["SELECT goalid, timeestimate, target
                     FROM task
                     WHERE iterationid = ?"
             iterationid])
   (group-by :goalid)
   (map-vals #(map parse-time-val %))
   (map-vals #(group-by second %))
   (map-vals #(map (fn [[unit estimates]] [unit (apply + (map first estimates))]) %))
   (map-vals #(into {} %))
   (map-vals #(util/normalize-time %))))

(defn goals-with-estimates [db iterationid]
  (let [estimates (total-estimates db iterationid)]
    (map #(assoc %
                 :estimate
                 (second (find-first (fn [[k _]] (= k (:id %))) estimates)))
         (all db :goal))))
