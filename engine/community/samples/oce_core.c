#include <jni.h>
#include <stdio.h>
#include "OceJNIConnector.h"
#include <igraph.h>   

typedef struct {
	jclass string,
            node,
			edge;
} Classes;

int findClasses(JNIEnv *env, Classes* classes) {
	classes->string = (*env)->FindClass(env, "java/lang/String");
	if(classes->string == NULL || (*env)->ExceptionOccurred(env) != NULL) {
		return -1;
	}

	classes->node = (*env)->FindClass(env, "Node");
	if(classes->node == NULL || (*env)->ExceptionOccurred(env) != NULL) {
		return -1;
	}

    classes->edge = (*env)->FindClass(env, "Edge");
	if(classes->edge == NULL || (*env)->ExceptionOccurred(env) != NULL) {
		return -1;
	}

	return 0;
}

void objDebug(JNIEnv *env, jobject obj) {
    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID mid = (*env)->GetMethodID(env, cls, "getClass", "()Ljava/lang/Class;");
    jobject clsObj = (*env)->CallObjectMethod(env, obj, mid);
    // Now get the class object's class descriptor
    cls = (*env)->GetObjectClass(env, clsObj);

    // Find the getName() method on the class object
    mid = (*env)->GetMethodID(env,cls, "getName", "()Ljava/lang/String;");

    // Call the getName() to get a jstring object back
    jstring strObj = (jstring)(*env)->CallObjectMethod(env, clsObj, mid);

    // Now get the c string from the java jstring object
    const char* str = (*env)->GetStringUTFChars(env,strObj, NULL);

    // Print the class name
    printf("\nObject type is: %s\n", str);

    // Release the memory pinned char array
    (*env)->ReleaseStringUTFChars(env,strObj, str);
}

void classDebug(JNIEnv *env, const char * expectedType) {
    jclass cls = (*env)->FindClass(env, expectedType);

    // Get the class object's class descriptor
    jclass clsClazz = (*env)->GetObjectClass(env, cls);

    // Find the getSimpleName() method in the class object
    jmethodID methodId = (*env)->GetMethodID(env, clsClazz, "getCanonicalName", "()Ljava/lang/String;");
    jstring className = (jstring) (*env)->CallObjectMethod(env, cls, methodId);
    
    const char* str = (*env)->GetStringUTFChars(env, className, NULL);

    printf("\nClass type is: %s\n", str);

    // And finally, don't forget to release the JNI objects after usage!!!!
    (*env)->DeleteLocalRef(env, clsClazz);
    (*env)->DeleteLocalRef(env, cls);
}

//TODO - It is very unefficient to add edges one by one, instead, fill up vector and
//pass to igraph_add_edges function
void addGraphEdge( igraph_t *graph, igraph_integer_t start_unique_id,  igraph_integer_t end_unique_id) {
    igraph_vector_t v;
    printf("adding edge with %d and %d", start_unique_id, end_unique_id);
    //igraph_vector_init(&v, 2);
    //VECTOR(v)[0] = start_unique_id;
    //VECTOR(v)[1] = end_unique_id;
    igraph_add_edge(graph, start_unique_id, end_unique_id);
}

void addVertex( igraph_t *graph, igraph_integer_t unique_id) {
    igraph_vector_t v;
    //igraph_vector_init(&v, 2);
    //VECTOR(v)[0] = start_unique_id;
    //VECTOR(v)[1] = end_unique_id;
    //igraph_add_edge(graph, start_unique_id, end_unique_id);
}

JNIEXPORT jint JNICALL  Java_OceJNIConnector_graphSize(JNIEnv *env, jobject callingObject, jobjectArray inVertexArray, jobjectArray inEdgeArray)  {
    // Grab references to the classes we may need
	Classes classes;
	if (findClasses(env, &classes) == -1) {
		return -1; // Exception already thrown
	}
    jclass classEdge = classes.edge;
    jclass classNode = classes.node;

    //classDebug(env, "Edge");
    jmethodID getEdgeStartNodeMethod = (*env)->GetMethodID(env, classEdge, "getStartNode", "()LNode;");
    if (NULL == getEdgeStartNodeMethod) return -1;
    jmethodID getEdgeEndNodeMethod = (*env)->GetMethodID(env, classEdge, "getEndNode", "()LNode;");
    if (NULL == getEdgeEndNodeMethod) return -1;
    jmethodID getNodeIdMethod = (*env)->GetMethodID(env, classNode, "getId", "()Ljava/lang/String;");
    if (NULL == getNodeIdMethod) return -1;
    jmethodID getNodeUniqueIdMethod = (*env)->GetMethodID(env, classNode, "getUniqueId", "()I");
    if (NULL == getNodeUniqueIdMethod) return -1;
    // Get the value of each Integer object in the array
    jsize vertexArrLength = (*env)->GetArrayLength(env, inVertexArray);
    jsize edgeArrLength = (*env)->GetArrayLength(env, inEdgeArray);

    //we allow to create empty graph but if no vertices, there must be no edges 
    if (vertexArrLength == 0 && edgeArrLength > 0) return -1;

    igraph_t g;
    igraph_vector_t v1;

    if(edgeArrLength > 0) {
        igraph_vector_init(&v1, edgeArrLength);
    } else {
        igraph_empty(&g, 0, 0);
    }
    printf("\nnumber of edges from input: %d, number of vertices: %d", edgeArrLength, vertexArrLength);

    int i;
    for(i = 0; i < vertexArrLength; i++) {
        jobject objVertex = (*env)->GetObjectArrayElement(env, inVertexArray, i);
        if (NULL == objVertex) return -1;
        jint node_unique_id  = (*env)->CallIntMethod(env, objVertex, getNodeUniqueIdMethod);
        printf("\nnode unique id is %d ", node_unique_id);
    }

    jint sum = 0;
    
    for (i = 0; i < edgeArrLength; i++) {
        jobject objEdge = (*env)->GetObjectArrayElement(env, inEdgeArray, i);
        if (NULL == objEdge) return -1;
        classNode = (*env)->CallObjectMethod(env, objEdge, getEdgeStartNodeMethod);
        jstring start_id = (*env)->CallObjectMethod(env, classNode, getNodeIdMethod); 
        const char *startIdCStr = (*env)->GetStringUTFChars(env, start_id, 0); 
        //printf("\nnode id is %s ", startIdCStr);

        jint start_unique_id = (*env)->CallIntMethod(env, classNode, getNodeUniqueIdMethod); 
        //printf(" with unique id is %ld :", start_unique_id);

        classNode = (*env)->CallObjectMethod(env, objEdge, getEdgeEndNodeMethod);
        jstring end_id = (*env)->CallObjectMethod(env, classNode, getNodeIdMethod); 
        const char *endIdCStr = (*env)->GetStringUTFChars(env, end_id, 0); 
        //printf(" node id is %s", endIdCStr);

        jint end_unique_id = (*env)->CallIntMethod(env, classNode, getNodeUniqueIdMethod); 
        //printf(" with unique id is %ld", end_unique_id);
        VECTOR(v1)[i] = start_unique_id;
        VECTOR(v1)[++i] = end_unique_id;

        //addGraphEdge(&g, (igraph_integer_t)start_unique_id, (igraph_integer_t)end_unique_id);
    }

    igraph_create(&g, &v1, vertexArrLength, 0);

    if (igraph_vcount(&g) != vertexArrLength) {
        printf("there is a mismatch with number of vetices");
        return -1;
    }

    (*env)->DeleteLocalRef(env, classEdge);
    (*env)->DeleteLocalRef(env, classNode);

    int vcount = igraph_vcount(&g);
    printf("\nnumber of edges from igraph: %d\n", vcount);
    igraph_destroy(&g);

    return vcount;
}



