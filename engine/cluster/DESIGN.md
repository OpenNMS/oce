We maintain an in memory graph using the GraphManager.
When IOs are modified, the graph is updated.
When alarms are updated, the graph is updated.

The following facts are maintained in the working memory by the engine:
Drools:
 * Vertices that have 1+ alarms
 * All of the alarms
 * All of the situations
   * As received by the datasource (not those that have been submitted)
 * All of the feedback
   * Engine is expected to age out feedback as necessary, since it will not be deleted by the engine

Rules:
 1. "Garbage collection" should be performed by the rules - these rules should run first
 2. Clustering should be triggered by the rules, and the facts should be added to the working memory
    he rules can then drive how the situations are mapped and retract the facts from working memory once complete.
 3. The rules should be used to decide how the feedback is handled
