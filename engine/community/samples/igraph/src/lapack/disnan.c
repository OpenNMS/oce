/*  -- translated by f2c (version 20100827).
   You must link the resulting object file with libf2c:
	on Microsoft Windows system, link with libf2c.lib;
	on Linux or Unix systems, link with .../path/to/libf2c.a -lm
	or, if you install libf2c.a in a standard place, with -lf2c -lm
	-- in that order, at the end of the command line, as in
		cc *.o -lf2c -lm
	Source for libf2c is in /netlib/f2c/libf2c.zip, e.g.,

		http://www.netlib.org/f2c/libf2c.zip
*/

#include "f2c.h"

logical igraphdisnan_(doublereal *din)
{
    /* System generated locals */
    logical ret_val;

    /* Local variables */
    extern logical igraphdlaisnan_(doublereal *, doublereal *);


/*  -- LAPACK auxiliary routine (version 3.2.2) --   
    -- LAPACK is a software package provided by Univ. of Tennessee,    --   
    -- Univ. of California Berkeley, Univ. of Colorado Denver and NAG Ltd..--   
       June 2010   


    Purpose   
    =======   

    DISNAN returns .TRUE. if its argument is NaN, and .FALSE.   
    otherwise.  To be replaced by the Fortran 2003 intrinsic in the   
    future.   

    Arguments   
    =========   

    DIN     (input) DOUBLE PRECISION   
            Input to test for NaN.   

    ===================================================================== */

    ret_val = igraphdlaisnan_(din, din);
    return ret_val;
} /* igraphdisnan_ */

