#include <igraph.h>

void print_vector(igraph_vector_t *v, FILE *f) {
  long int i;
  for (i=0; i<igraph_vector_size(v); i++) {
    fprintf(f, " %li", (long int) VECTOR(*v)[i]);
  }
  fprintf(f, "\n");
}

int main() {

  igraph_t g;
  igraph_vector_t v;
  int ret;

  /* Create graph */
  igraph_vector_init(&v, 8);
  VECTOR(v)[0]=0; VECTOR(v)[1]=1;
  VECTOR(v)[2]=1; VECTOR(v)[3]=2;
  VECTOR(v)[4]=2; VECTOR(v)[5]=3;
  VECTOR(v)[6]=2; VECTOR(v)[7]=2;
  igraph_create(&g, &v, 0, 1);

  /* Add edges */
  igraph_vector_resize(&v, 4);
  VECTOR(v)[0]=2; VECTOR(v)[1]=1;
  VECTOR(v)[2]=3; VECTOR(v)[3]=3;
  igraph_add_edges(&g, &v, 0);
  
  /* Check result */
  igraph_get_edgelist(&g, &v, 0);
  igraph_vector_sort(&v);
  print_vector(&v, stdout);

  /* Error, vector length */
  igraph_set_error_handler(igraph_error_handler_ignore);
  igraph_vector_resize(&v, 3);
  VECTOR(v)[0]=0; VECTOR(v)[1]=1;
  VECTOR(v)[2]=2;
  ret=igraph_add_edges(&g, &v, 0);
  if (ret != IGRAPH_EINVEVECTOR) {
    return 1;
  }
  
  /* Check result */
  igraph_get_edgelist(&g, &v, 0);
  igraph_vector_sort(&v);
  print_vector(&v, stdout);

  /* Error, vector ids */
  igraph_vector_resize(&v, 4);
  VECTOR(v)[0]=0; VECTOR(v)[1]=1;
  VECTOR(v)[2]=2; VECTOR(v)[3]=4;
  ret=igraph_add_edges(&g, &v, 0);
  if (ret != IGRAPH_EINVVID) {
    return 2;
  }  

  /* Check result */
  igraph_get_edgelist(&g, &v, 0);
  igraph_vector_sort(&v);
  print_vector(&v, stdout);

  igraph_vector_destroy(&v);
  igraph_destroy(&g);

  return 0;
}