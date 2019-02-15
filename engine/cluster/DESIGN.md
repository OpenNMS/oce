We maintain an in memory graph using the GraphManager.
When IOs are modified, the graph is updated.
When alarms are updated, the graph is updated.

Drools:
 * Vertices that have 1+ alarms are maintained in the working memory
 * All of the alarms are maintained in the working memory
 * All of the situations are maintained in the working memory
 * All of the feedback is maintained in working memory

Rules:
 1. "Garbage collection" should be performed by the rules - these rules should run first
 2. Clustering should be triggered by the rules, and the facts should be added to the working memory
    he rules can then drive how the situations are mapped and retract the facts from working memory once complete.
 3. The rules should be used to decide how the feedback is handled
