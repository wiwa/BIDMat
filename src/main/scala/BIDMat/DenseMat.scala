package BIDMat
import scala.math.Numeric._
import scala.reflect._
import java.util.Arrays
import java.util.Comparator
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
//import scala.tools.nsc.interpreter

class DenseMat[@specialized(Double,Float,Int,Byte,Long) T]
(_dims:Array[Int], val _data:Array[T])(implicit manifest:ClassTag[T]) extends Mat(_dims)  {
  
  def this(nr:Int, nc:Int, data:Array[T])(implicit manifest:ClassTag[T]) = this(Array(nr, nc), data);
  
  def this(dims0:Array[Int])(implicit manifest:ClassTag[T]) = this(
		  {
	       if (Mat.debugCPUmem) {
	    	   val len = dims0.reduce(_*_);
	    	   println("DenseMat %d" format len);
	    	   if (len > Mat.debugMemThreshold) throw new RuntimeException("DenseMat alloc too large");
	       }
	       dims0;
      },
      new Array[T](dims0.reduce(_*_)));
  
  def this(nr:Int, nc:Int)(implicit manifest:ClassTag[T]) = {
	  this({if (Mat.debugCPUmem) {
		  println("DenseMat %d %d" format (nr, nc))
		  if (nr*nc > Mat.debugMemThreshold) throw new RuntimeException("DenseMat alloc too large");
	  }
	  Array(nr, nc);}, new Array[T](nr*nc));
  }

  /** Return the (0,0) value as a scalar. */
  def v:T =
    if (nrows > 1 || ncols > 1) {
      throw new RuntimeException("Matrix should be 1x1 to extract value")
    } else {
      _data(0)
    }
  
  /** Returns a string description of this type, i.e., returns "DenseMat". */
  override def mytype = "DenseMat"

  /** Returns true if this matrix is a row or column vector, false otherwise. */
  def isvector(): Boolean = {
    if (nrows == 1 || ncols == 1) {
      true
    } else {
      false
    }
  }

  /** Bounds-checked matrix access, 0- or 1-based. */ 
  def gapply(r0:Int, c0:Int):T = {
    val off = Mat.oneBased
    val r = r0 - off
    val c = c0 - off
    if (r < 0 || r >= nrows || c < 0 || c >= ncols) {
      throw new IndexOutOfBoundsException("("+(r+off)+","+(c+off)+") vs ("+nrows+","+ncols+")");
    } else {
    	_data(r+c*nrows)
    }
  }

  /** Bounds-checked linear access, 0- or 1-based. */ 
  def gapply(i0:Int):T = {
    val off = Mat.oneBased
    val i = i0 - off
    if (i < 0 || i >= length) {
      throw new IndexOutOfBoundsException(""+(i+off)+" >= ("+length+")");
    } else {
      _data(i)
    }
  } 
  
  def apply(i:Int, j:Int):T = gapply(i, j);
  
  def apply(i:Int):T = gapply(i);

  /** Unchecked 0-based matrix access of element at m(r,c). */ 
  def get_(r:Int, c:Int):T = {
    _data(r+c*nrows)
  }
  
  /** 
   * Returns a single index using linear access (i.e., column-major order) of first occurrence of element '''a''', 
   * or -1 if it does not exist. 
   * 
   * Note that matrices can be 0- or 1-based; the latter occurs in languages like MATLAB. In that case, an 
   * element not present in the matrix gets an index of 0 instead of 1.
   * 
   * In the following examples, the matrix is 0-based:
   * 
   * {{{
   * scala> val a = 3\4 on 7\4
   * a: BIDMat.IMat =
   *    3   4
   *    7   4
   * 
   * scala> a.indexOf(7)
   * res20: Int = 1
   * 
   * scala> a.indexOf(4)
   * res21: Int = 2
   * }}}
   */
  def indexOf(a:T):Int = {
    _data.indexOf(a) + Mat.oneBased
  }
  
  /**
   * Returns a tuple representing the (row, column) index of element '''a''' in this matrix, or (-1,0) if it
   * does not exist and if the matrix is 0-based. 1-based arrays get (0,1) for a non-existent element.
   * 
   * In the following examples, the matrix is 0-based:
   * 
   * {{{
   * scala> val a = 3\4 on 7\4
   * a: BIDMat.IMat =
   *    3   4
   *    7   4
   *
   * scala> a.indexOf2(7)
   * res22: (Int, Int) = (1,0)
   *
   * scala> a.indexOf2(4)
   * res23: (Int, Int) = (0,1)
   * }}}
   */
  def indexOf2(a:T):(Int, Int) = {
    val off = Mat.oneBased
    val v = _data.indexOf(a)
    (v % nrows + off, v / nrows + off)
  }
  
  /** Update a matrix value, m(r,c) = v, 0- or 1-based. */
  def _update(r0:Int, c0:Int, v:T):T = {
    val off = Mat.oneBased
    val r = r0 - off
    val c = c0 - off
    if (r < 0 || r >= nrows || c < 0 || c >= ncols) {
      throw new IndexOutOfBoundsException("("+(r+off)+","+(c+off)+") vs ("+nrows+","+ncols+")");
    } else {
      _data(r+c*nrows) = v
    }
    v
  }

  /** Update a matrix value with linear access, m(i) = v. */
  def _update(i0:Int, v:T):T = {
    val off = Mat.oneBased
    val i = i0 - off
    if (i < 0 || i >= length) {
      throw new IndexOutOfBoundsException(""+(i+off)+" vs ("+length+")");
    } else {
      _data(i) = v
    }
    v
  }

  /** Unchecked 0-based set, so m(r,c) = v. */ 
  def set_(r:Int, c:Int, v:T):T = {
    _data(r+c*nrows) = v
    v
  } 

  /** Returns the transpose of this matrix. */ 
  def gt(oldmat:Mat):DenseMat[T]  = {
    var out:DenseMat[T] = DenseMat.newOrCheck(ncols, nrows, oldmat, GUID, "gt".hashCode)
    var i = 0
    while (i < nrows) {
      var j = 0
      while (j < ncols) {
        out._data(j+i*ncols) = _data(i+j*nrows)
        j += 1
      }
      i += 1
    }
    out
  }

  /**
   * Stack matrices vertically.
   * 
   * Throws a RuntimeException if the number of columns does not match.
   * 
   * Note: As is usual with DenseMat methods, it will return a "correct" matrix in that arithmetic, etc.
   * should work, but it is not visible on the command line, so wrap it around with (for instance) a
   * DMat to "see" the results on the command line.
   * 
   * Example:
   * {{{
   * scala> val b = DMat(1\2 on 3\4)
   * b: BIDMat.DMat =
   *    1   2
   *    3   4
   * 
   * scala> b.gvertcat(b)
   * res12: BIDMat.DenseMat[Double] =
   * 
   * 
   * 
   * 
   * 
   * scala> DMat(b.gvertcat(b))
   * res13: BIDMat.DMat =
   *    1   2
   *    3   4
   *    1   2
   *    3   4
   * 
   * scala> 
   * }}}
   */
  def gvertcat(a:DenseMat[T]):DenseMat[T] = 
    if (ncols != a.ncols) {
      throw new RuntimeException("ncols must match")
    } else {
      var out = DenseMat.newOrCheck(nrows+a.nrows, ncols, null, GUID, a.GUID, "on".hashCode)
      var i = 0
      while (i < ncols) {
        System.arraycopy(_data, i*nrows, out._data, i*(nrows+a.nrows), nrows)
        System.arraycopy(a._data, i*a.nrows, out._data, nrows+i*(nrows+a.nrows), a.nrows)
        i += 1
      }
      out
    }

  /** 
   * Stack matrices horizontally. 
   * 
   * Throws a RuntimeException if the number of rows does not match.
   * 
   * Note: As is usual with DenseMat methods, it will return a "correct" matrix in that arithmetic, etc.
   * should work, but it is not visible on the command line, so wrap it around with (for instance) a
   * DMat to "see" the results on the command line.
   *  
   * Example:
   * {{{
   * scala> val b = DMat(1\2 on 3\4)
   * b: BIDMat.DMat =
   *    1   2
   *    3   4
   * 
   * scala> b.ghorzcat(b)
   * res10: BIDMat.DenseMat[Double] =
   * 
   * 
   * 
   * scala> DMat(b.ghorzcat(b))
   * res11: BIDMat.DMat =
   *    1   2   1   2
   *    3   4   3   4 
   * }}}
   */ 
  def ghorzcat(a:DenseMat[T]):DenseMat[T]= 
    if (nrows != a.nrows) {
      throw new RuntimeException("nrows must match")
    } else {
    	val newdims = _dims.clone;
    	newdims(dims.length-1) = ncols + a.ncols;      
      var out = DenseMat.newOrCheck(newdims, null, GUID, a.GUID, "\\".hashCode)
      System.arraycopy(_data, 0, out._data, 0, nrows*ncols)
      System.arraycopy(a._data, 0, out._data, nrows*ncols, nrows*a.ncols)
      out
    }

  /** Count number of non-zero entries. */
  override def nnz:Int = {
    var count:Int = 0
    var i = 0
    while (i < length) {
      if (_data(i) != 0) {
        count += 1
      }
      i += 1
    }
    count
  }

  /** Helper function for find functions. */ 
  def findInds(out:IMat, off:Int):IMat = {
    var count = 0
    var i = off
    while (i < length+off) {
      if (_data(i) != 0) {
        out._data(count) = i
        count += 1
      } 
      i += 1
    }
    out
  }

  /** Find indices (linear) for all non-zeros elements. */
  def find:IMat = {
    var out = IMat.newOrCheckIMat(nnz, 1, null, GUID, "find1".hashCode)
    findInds(out, Mat.oneBased)
  }  

  /** Find indices (i,j) for non-zero elements. */ 
  def find2:(IMat, IMat) = {
    val iout = IMat.newOrCheckIMat(nnz, 1, null, GUID, "find2_1".hashCode)
    val jout = IMat.newOrCheckIMat(nnz, 1, null, GUID, "find2_2".hashCode)
    findInds(iout, 0)
    val off = Mat.oneBased
    var i = 0
    while (i < iout.length) {
      val ival:Int = iout._data(i)
      jout._data(i) = (ival / nrows) + off
      iout._data(i) = (ival % nrows) + off
      i += 1
    }
    (iout, jout)
  } 

  /** Find tuples (i,j,v) for non-zero elements. */ 
  def gfind3:(IMat, IMat, DenseMat[T]) = {
    val iout = IMat.newOrCheckIMat(nnz, 1, null, GUID, "gfind3_1".hashCode)
    val jout = IMat.newOrCheckIMat(nnz, 1, null, GUID, "gfind3_2".hashCode)
    val vout = DenseMat.newOrCheck(nnz, 1, null, GUID, "gfind3_3".hashCode)
    findInds(iout, 0)
    val off = Mat.oneBased
    var i = 0
    while (i < iout.length) {
      val ival:Int = iout._data(i)
      vout._data(i) = _data(ival)
      jout._data(i) = (ival / nrows) + off
      iout._data(i) = (ival % nrows) + off
      i += 1
    }
    (iout, jout, vout)
  }  

  /** Return a(im) where im is a matrix of indices. */
  def gapply(im:IMat):DenseMat[T] = 
    im match {
      case aa:MatrixWildcard => {
        val out = DenseMat.newOrCheck(length, 1, null, GUID, im.GUID, "gapply1dx".hashCode)
        System.arraycopy(_data, 0, out._data, 0, out.length)
        out
      }
      case _ => {
        val out = DenseMat.newOrCheck(im._dims, null, GUID, im.GUID, "gapply1d".hashCode)
        var i = 0
        val off = Mat.oneBased
        while (i < out.length) {
          val ind = im._data(i) - off
          if (ind < 0 || ind >= length) {
            throw new RuntimeException("bad linear index "+(ind+off)+" vs "+length)
          } else {
            out._data(i) = _data(ind)
          }
          i += 1
        }
        out
      }
    } 
  
  /** Implement a(im) = b where im is a matrix of indices to a and im and b are same-sized. */
  def _update(im:IMat, b:DenseMat[T]):DenseMat[T] = 
    im match {
      case aaa:MatrixWildcard => {
        if (length != b.length || b.ncols != 1) {
          throw new RuntimeException("dims mismatch")
        } else {
          System.arraycopy(b._data, 0, _data, 0, length)
        }
        b
      }
      case _ => {
        if (im.nrows != b.nrows || im.ncols != b.ncols) {
          throw new RuntimeException("dims mismatch")
        } else {
        	val off = Mat.oneBased
          var i = 0
          while (i < im.length) {
            val ind = im._data(i) - off
            if (ind < 0 || ind >= length) {
              throw new RuntimeException("bad linear index "+(ind+off)+" vs "+length)
            } else {
              _data(ind) = b._data(i)
            }
            i += 1
          }
        }
        b
      }
    } 
  
  /** Implement a(im) = b where im is a matrix of indices to a, and b is a constant. */
  def _update(inds:IMat, b:T):DenseMat[T] = {
    inds match {
  		case aaa:MatrixWildcard => {
  			var i = 0
  			while (i < length) {
  				_data(i) = b
  				i += 1
  			}
  		}
  		case _ => {
  			var i = 0
  			val off = Mat.oneBased
  			while (i < inds.length) {
  				val ind = inds._data(i) - off
  				if (ind < 0 || ind >= length) {
  					throw new RuntimeException("bad linear index "+(ind+off)+" vs "+length)
  				} else {
  					_data(ind) = b
  				}
  				i += 1
  			}
  		}
    }  
    this
  }
  
  /** Throws exception if a string is within a limited index range in a string matrix. */
  def checkInds(inds:IMat, limit:Int, typ:String) = {
  	val off = Mat.oneBased
  	var i = 0
  	while (i < inds.length) {
  		val r = inds._data(i)-off
  		if (r >= limit) throw new RuntimeException(typ+ " index out of range %d %d" format (r, limit))
  		i += 1
  	}
  }

  /** Implement slicing, a(iv,jv) where iv and jv are vectors, using ? as wildcard. */
  def gapply(rowinds:IMat, colinds:IMat):DenseMat[T] = {
  	var out:DenseMat[T] = null
  	val off = Mat.oneBased
  	rowinds match {
  	case dummy:MatrixWildcard => {
  		colinds match {
  		case dummy2:MatrixWildcard => {
  			out = DenseMat.newOrCheck(nrows, ncols, null, GUID, rowinds.GUID, colinds.GUID, "gapply2d".hashCode)
  			System.arraycopy(_data, 0, out._data, 0, length)
  		}
  		case _ => {
  			out = DenseMat.newOrCheck(nrows, colinds.length, null, GUID, rowinds.GUID, colinds.GUID, "gapply2d".hashCode)
  			var i = 0 
  			while (i < colinds.length) {
  				val c = colinds._data(i) - off
  				if (c >= ncols) throw new RuntimeException("col index out of range %d %d" format (c, ncols))
  				System.arraycopy(_data, c*nrows, out._data, i*nrows, nrows)
  				i += 1
  			}
  		}
  		}
  	}
  	case _ => {
  		checkInds(rowinds, nrows, "row") 
  		colinds match {
  		case dummy2:MatrixWildcard => {
  			out = DenseMat.newOrCheck(rowinds.length, ncols, null, GUID, rowinds.GUID, colinds.GUID, "gapply2d".hashCode)
  			var i = 0
  			while (i < ncols) {
  				var j = 0
  				while (j < out.nrows) {
  					val r = rowinds._data(j)-off
  					out._data(j+i*out.nrows) = _data(r+i*nrows)
  					j += 1
  				}
  				i += 1
  			}
  		}
  		case _ => {
  			out = DenseMat.newOrCheck(rowinds.length, colinds.length, null, GUID, rowinds.GUID, colinds.GUID, "gapply2d".hashCode)
  			var i = 0
  			while (i < out.ncols) {
  				var j = 0
  				val c = colinds._data(i) - off
  				if (c >= ncols) throw new RuntimeException("col index out of range %d %d" format (c, ncols))
  				while (j < out.nrows) {
  					val r = rowinds._data(j)-off
  					out._data(j+i*out.nrows) = _data(r+nrows*c)
  					j += 1
  				}
  				i += 1
  			}
  		}
  		}
  	}
  }
  out
  }

  /** Tries to save a slice into an output matrix, but recreates it if too small. */
  def gcolslice(a:Int, b:Int, omat:Mat, c:Int):DenseMat[T] = {
    val off = Mat.oneBased;
    val newdims = _dims.clone;
    newdims(dims.length-1) = b-a;
    val out = DenseMat.newOrCheck[T](newdims, omat, GUID, a, b-a+c-off, "gcolslice".##)
    if (a-off < 0) throw new RuntimeException("colslice index out of range %d" format (a))
    if (b-off > ncols) throw new RuntimeException("colslice index out of range %d %d" format (b, ncols))
    
    System.arraycopy(_data, (a-off)*nrows, out._data, (c-off)*nrows, (b-a)*nrows)
    out
  }
  
  /** Tries to save a slice into an output matrix, but recreates it if too small. */
  def growslice(a:Int, b:Int, omat:Mat, c:Int):DenseMat[T] = {
    val off = Mat.oneBased
    val out = DenseMat.newOrCheck[T](b-a+c-off, ncols, omat, GUID, a, b-a+c-off, "growslice".##)
    if (a-off < 0) throw new RuntimeException("rowslice index out of range %d" format (a))
    if (b-off > nrows) throw new RuntimeException("rowslice index out of range %d %d" format (b, nrows))
    var i = 0
    while (i < ncols) {
      System.arraycopy(_data, (a-off)+i*nrows, out._data, (c-off)+i*out.nrows, (b-a))
      i += 1
    }    
    out
  }
  
  /** Implement slicing, a(iv,j) where iv a vector, j an integer, using ? as wildcard. */
  def gapply(iv:IMat, jv:Int):DenseMat[T] = {
  		gapply(iv, IMat.ielem(jv))
  }

  /** Implement slicing, a(i,jv) where i integer, jv a vector, using ? as wildcard. */
  def gapply(i:Int, jv:IMat):DenseMat[T] = {
  		gapply(IMat.ielem(i), jv)
  }

  /** Implement sliced assignment, a(iv,jv) = b where iv and jv are vectors, using ? as wildcard. */ 
  def _update(rowinds:IMat, colinds:IMat, b:DenseMat[T]):DenseMat[T] = {
  	val off = Mat.oneBased
  	rowinds match {
  	case dummy:MatrixWildcard => {
  		colinds match {
  		case dummy2:MatrixWildcard => {
  			if (nrows != b.nrows || ncols != b.ncols) {
  				throw new RuntimeException("dims mismatch in assignment")
  			}
  			System.arraycopy(b._data, 0, _data, 0, length) 
  		}
  		case _ => {
  			if (nrows != b.nrows || colinds.length != b.ncols) {
  				throw new RuntimeException("dims mismatch in assignment")
  			}
  			var i = 0 
    		while (i < colinds.length) {
    			val c = colinds._data(i) - off
    		  if (c >= ncols) throw new RuntimeException("col index out of range %d %d" format (c, ncols))
    			System.arraycopy(b._data, i*nrows, _data, c*nrows, nrows)
    			i += 1
    		}
  		}
  		}
  	}
    case _ => {
      checkInds(rowinds, nrows, "row") 
    	colinds match {
    	case dummy2:MatrixWildcard => {
    		if (rowinds.length != b.nrows || ncols != b.ncols) {
  				throw new RuntimeException("dims mismatch in assignment")
  			}
    		var i = 0
    		while (i < ncols) {
    		  var j = 0
    		  while (j < b.nrows) {
    		  	val r = rowinds._data(j)-off
    		  	_data(r+i*nrows) = b._data(j+i*b.nrows) 
    		    j += 1
    		  }
    		  i += 1
    		}
    	}
    	case _ => {
    		if (rowinds.length != b.nrows || colinds.length != b.ncols) {
  				throw new RuntimeException("dims mismatch in assignment")
  			}
    		var i = 0
    		while (i < b.ncols) {
    			val c = colinds._data(i) - off
    			if (c >= ncols) throw new RuntimeException("col index out of range %d %d" format (c, ncols))
    			var j = 0
    			while (j < b.nrows) {
    			  val r = rowinds._data(j)-off
    				_data(r+nrows*c) = b._data(j+i*b.nrows)
    				j += 1
    			}
    			i += 1
    		}
    	}    		  
      }
    }
  	}
    this
  }
  
  /** Sliced assignment, where m(iv,jv) = b. Varies depending on type of matrices involved. */
  override def update(iv:IMat, jv:IMat, b:Mat):Mat = {
    (this, b) match {
      case (me:FMat, bb:FMat) => me.update(iv, jv, bb):FMat
      case (me:DMat, bb:DMat) => me.update(iv, jv, bb):DMat
      case (me:IMat, bb:IMat) => me.update(iv, jv, bb):IMat
      case (me:CMat, bb:CMat) => me.update(iv, jv, bb):CMat
    }
  }
  
  /** Implement sliced assignment, a(iv,jv) = b:T where iv and jv are vectors, using ? as wildcard. */ 
  def _update(rowinds:IMat, colinds:IMat, b:T):DenseMat[T] = {
  	val off = Mat.oneBased
  	rowinds match {
  	case dummy:MatrixWildcard => {
  		colinds match {
  		case dummy2:MatrixWildcard => {
  			var i = 0 
  			while (i < length) {
  			  _data(i) = b
  			  i += 1
  			}
  		}
  		case _ => {
  			var i = 0 
    		while (i < colinds.length) {
    			val c = colinds._data(i) - off
    		  if (c >= ncols) throw new RuntimeException("col index out of range %d %d" format (c, ncols))
    			var j = 0
    			while (j < nrows) {
    			  _data(j + c*nrows) = b
    			  j += 1
    			}
    			i += 1
    		}
  		}
  		}
  	}
    case _ => {
      checkInds(rowinds, nrows, "row") 
    	colinds match {
    	case dummy2:MatrixWildcard => {
    		var i = 0
    		while (i < ncols) {
    		  var j = 0
    		  while (j < rowinds.length) {
    		  	val r = rowinds._data(j)-off
    		  	_data(r+i*nrows) = b 
    		    j += 1
    		  }
    		  i += 1
    		}
    	}
    	case _ => {
    		var i = 0
    		while (i < colinds.length) {
    			val c = colinds._data(i) - off
    			if (c >= ncols) throw new RuntimeException("col index out of range %d %d" format (c, ncols))
    			var j = 0
    			while (j < rowinds.length) {
    			  val r = rowinds._data(j)-off
    				_data(r+nrows*c) = b
    				j += 1
    			}
    			i += 1
    		}
    	}    		  
      }
    }
  	}
    this
  }
  
  /** Implement sliced assignment, a(iv,j) = b where iv a vectors, j integer, using ? as wildcard. */ 
  def _update(iv:IMat, j:Int, b:T):DenseMat[T] = {
    _update(iv, IMat.ielem(j), b)
  }

  /** Implement sliced assignment, a(i,jv) = b where jv a vector, using ? as wildcard. */ 
  def _update(i:Int, jv:IMat, b:T):DenseMat[T] = {
    _update(IMat.ielem(i), jv, b)
  }
  
  /** Prints '''i''' spaces, useful for building strings. */
  override def printOne(i:Int):String = " "
  
  /** Returns a string representation of the matrix. */
  override def toString:String = {
    if (dims.length == 2) {
      twoDtoString;
    } else {
    	NDtoString;
    }
  }
  
  val somespaces = "                                             "

  def twoDtoString:String = {
    val sb:StringBuilder = new StringBuilder
    val maxlen = 64000;
    if (nrows == 1) {
      if (ncols > 0) sb.append(printOne(0))
      var i = 1
      while (i < math.min(maxlen/4, ncols)) {
    	sb.append(",")
    	sb.append(printOne(i))
    	i += 1
      }
    } else {
      val nChars = Mat.terminalWidth-4
      val maxRows = maxlen/nChars
      var maxCols = nChars
      var fieldWidth = 4
      var icols = 0
      while (icols < math.min(ncols, maxCols)) {
    	var newWidth = fieldWidth
    	for (j <- 0 until math.min(nrows,maxRows)) newWidth = math.max(newWidth, 2+(printOne(j+nrows*icols).length))
    	if ((icols+1)*newWidth < nChars) {
    		fieldWidth = newWidth
    		icols += 1
    	} else {
    		maxCols = icols
    	}
      }    	
        for (i <- 0 until math.min(nrows, maxRows)) {
          for (j <- 0 until math.min(ncols, icols)) {
    	    val str = printOne(i+j*nrows)
    		sb.append(somespaces.substring(0,fieldWidth-str.length)+str)
    	  }
    	  if (ncols > icols) {
    	    sb.append("...")
          }
    	  sb.append("\n")
    	}
    	if (nrows > maxRows) {
    	  for (j <- 0 until math.min(ncols, maxCols)) {
    	  	sb.append(somespaces.substring(0, fieldWidth-2)+"..")
    	  }
    	  sb.append("\n")
        }
    }
    sb.toString()
  }
  
   
  def NDtoString:String = {
    val maxlen = 64000
    val sb:StringBuilder = new StringBuilder();
    val nChars = Mat.terminalWidth-4;
    val ncols = prodDimsBy(1,2);
    val nrows = prodDimsByX(0,2);
    val maxRows = math.min(maxlen/nChars, nrows);
    var maxCols = math.min(nChars, ncols);
    var fieldWidth = 4;
    val cs = populateCS(maxRows, maxCols);
    val ws = new IMat(maxRows, maxCols, cs.data.map(_.length));
    var icols = 0;
    val colinds = new Array[Int](_dims.length / 2);
    val oddDims = subDims(1, 2);
    while (icols < maxCols) {
      var newWidth = fieldWidth;
      for (j <- 0 until maxRows) newWidth = math.max(newWidth, 2+(cs(j, icols).length));
          if ((icols+1)*newWidth < nChars) {
            fieldWidth = newWidth;
            icols += 1;
          } else {
            maxCols = icols;
          }
    }     
    for (i <- 0 until maxRows) {
      Arrays.fill(colinds, 0)
      for (j <- 0 until icols) {
        val str = cs(i,j);
        val ncarry = incInds(colinds, oddDims);
        sb.append(somespaces.substring(0,fieldWidth-str.length)+str+somespaces.substring(0,ncarry));
      }
      if (ncols > icols) {
        sb.append("...");
      }
      sb.append("\n");
    }
    sb.toString()
  }
  
  /** Clears the elements of a matrix by filling in 0s and nulls. */
  override def clear:DenseMat[T] ={
    if (length == 0) {
      this
    } else {
      val v = _data(0)
      v match {
        case a:Float => Arrays.fill(_data.asInstanceOf[Array[Float]], 0, length, 0)
        case a:Double => Arrays.fill(_data.asInstanceOf[Array[Double]], 0, length, 0)
        case a:Int => Arrays.fill(_data.asInstanceOf[Array[Int]], 0, length, 0)
        case a:Long => Arrays.fill(_data.asInstanceOf[Array[Long]], 0, length, 0)
        case _ => Arrays.fill(_data.asInstanceOf[Array[AnyRef]], 0, length, null)
      }
    }
    this
  }
  
  /**
   * Sets some upper right triangular parts of a matrix to be '''v'''; actual elements depend on '''off'''.
   * 
   * Examples:
   * 
   * {{{
   * scala> val a = ones(3,3)
   * a: BIDMat.FMat =
   *    1   1   1
   *    1   1   1
   *    1   1   1
   * 
   * scala> a.setUpper(2,1)
   * res52: BIDMat.DenseMat[Float] =
   *    2   2   2
   *    1   2   2
   *    1   1   2
   * 
   * scala> val b = ones(3,3)
   * b: BIDMat.FMat =
   *    1   1   1
   *    1   1   1
   *    1   1   1
 
   * scala> b.setUpper(2,0)
   * res53: BIDMat.DenseMat[Float] =
   *    1   2   2
   *    1   1   2
   *    1   1   1
   * }}}
   * 
   * @param v The element which we assign to certain positions of the matrix's elements.
   * @param off Determines the "first" diagonal of the matrix to which we assign v, and all diagonals
   *   above that will also be assigned v.
   */
  def setUpper(v:T, off:Int) = {
  	var i = 0
  	while (i < ncols) {
  		var j = 0
  		while (j < i+off) {
  			_data(j + i*nrows) = v
  			j += 1
  		}
  		i += 1
  	}
    this
  }
  
  /**
   * Similar to setUpper(v,off), except we set the lower left part of the matrix.
   * 
   * Examples:
   * {{{
   * scala> val a = ones(3,3)
   * a: BIDMat.FMat =
   *    1   1   1
   *    1   1   1
   *    1   1   1
   * 
   * scala> a.setLower(2,2)
   * res56: BIDMat.DenseMat[Float] =
   *    1   1   1
   *    1   1   1
   *    1   1   1
   * 
   * scala> a.setLower(2,1)
   * res57: BIDMat.DenseMat[Float] =
   *    1   1   1
   *    1   1   1
   *    2   1   1 
   * }}}
   * 
   * @param v The element which we assign to certain positions of the matrix's elements.
   * @param off Determines the "first" diagonal of the matrix to which we assign v, and all diagonals
   *   below that will also be assigned v.
   */
  def setLower(v:T, off:Int) = {
  	var i = 0
  	while (i < ncols) {
  		var j = math.max(0,i+1+off)
  		while (j < nrows) {
  			_data(j + i*nrows) = v
  			j += 1
  		}
  		i += 1
  	}
    this
  }

  /** General operation between two matrices. Apply op2 to corresponding elements from the input matrices. */
  def ggMatOp(aa:DenseMat[T], op2:(T,T) => T, oldmat:Mat):DenseMat[T] = {
    val (nr, nc, istep, jstep, istepa, jstepa) = ND.compatibleDims(_dims, aa._dims, "DenseMat Op");
    val dims = ND.maxDims(_dims, aa._dims);
    val out = DenseMat.newOrCheck[T](dims, oldmat, GUID, aa.GUID, op2.hashCode);
    Mat.nflops += nr * nc;
    var j = 0;
    while (j < nc) {
    	var i = 0;
    	while (i < nr) {
    		out._data(i+j*nr) = op2(_data(i*istep+j*jstep), aa._data(i*istepa+j*jstepa));
    		i += 1;
    	}
    	j += 1;
    }
    out;
  }

  /** Apply the binary operation op2 to the matrix and a scalar argument. */  
  def ggMatOpScalar(a:T, op2:(T,T) => T, oldmat:Mat):DenseMat[T] = {
    val out = DenseMat.newOrCheck[T](_dims, oldmat, GUID, a.hashCode, op2.hashCode)
    Mat.nflops += length
    var i  = 0
    while (i < length) {
      out._data(i) = op2(_data(i), a)
      i += 1
    }
    out
  }

  /**
   * General operation between two matrices. Apply op2 to corresponding elements from the input matrices.
   * Implemented with vector operation primitives.
   */
  def ggMatOpv(aa:DenseMat[T], opv:(Array[T],Int,Int,Array[T],Int,Int,Array[T],Int,Int,Int) => T, oldmat:Mat):DenseMat[T] = {
  		val (nr, nc, istep, jstep, istepa, jstepa) = ND.compatibleDims(_dims, aa._dims, "DenseMat Op");
  		val dims = ND.maxDims(_dims, aa._dims);
  		val out = DenseMat.newOrCheck[T](dims, oldmat, GUID, aa.GUID, opv.hashCode);
  		Mat.nflops += nr * nc;
  		var i = 0;         
  		while (i < nc) {
  			opv(_data, i*jstep, istep, aa._data, i*jstepa, istepa, out._data, i*nr, 1, nr);
  			i += 1;
  		}
  		out;
  }
  // TODO
  def ggMatOpStrictv(aa:DenseMat[T], opv:(Array[T],Int,Int,Array[T],Int,Int,Array[T],Int,Int,Int) => T, oldmat:Mat):DenseMat[T] = {
        var out:DenseMat[T] = null
        var mylen = 0
        if ((nrows==aa.nrows && ncols==aa.ncols) || (aa.nrows == 1 && aa.ncols == 1)) {
        	out = DenseMat.newOrCheck[T](nrows, ncols, oldmat, GUID, aa.GUID, opv.hashCode)
        	mylen = length
        } else if (nrows == 1 && ncols == 1) {
        	out = DenseMat.newOrCheck[T](aa.nrows, aa.ncols, oldmat, GUID, aa.GUID, opv.hashCode)
        	mylen = aa.length
        } else {
          throw new RuntimeException("dims incompatible (%s, %s)".format(
            Arrays.toString(dims.data), Arrays.toString(aa.dims.data)))
        }
        if (mylen > 100000 && Mat.numThreads > 1) {
        	val done = IMat(1, Mat.numThreads)
        	for (ithread<- 0 until Mat.numThreads) {
        		val istart = (1L*ithread*mylen/Mat.numThreads).toInt
        		val len = (1L*(ithread+1)*mylen/Mat.numThreads).toInt - istart
        		Future {
        			if (nrows==aa.nrows && ncols==aa.ncols) {
        				opv(_data, istart, 1, aa._data, istart, 1, out._data, istart, 1, len)
        			} else if (aa.nrows == 1 && aa.ncols == 1) {
        				opv(_data, istart, 1, aa._data, 0, 0, out._data, istart, 1, len)
        			} else {
        				opv(_data, 0, 0, aa._data, istart, 1, out._data, istart, 1, len)
        			}
        			done(ithread) = 1
        		}
        	}
        	while (SciFunctions.sum(done).v < Mat.numThreads) {Thread.`yield`()}         
        } else if (nrows==aa.nrows && ncols==aa.ncols) {
        	opv(_data, 0, 1, aa._data, 0, 1, out._data, 0, 1, aa.length)
        } else if (aa.nrows == 1 && aa.ncols == 1) {
          opv(_data, 0, 1, aa._data, 0, 0, out._data, 0, 1, length)
        } else if (nrows == 1 && ncols == 1) {
          opv(_data, 0, 0, aa._data, 0, 1, out._data, 0, 1, aa.length)
        } 
        Mat.nflops += mylen
        out
      }
  
  // TODO
  def ggMatOpScalarv(a:T, opv:(Array[T],Int,Int,Array[T],Int,Int,Array[T],Int,Int,Int) => T, oldmat:Mat):DenseMat[T] = {
    val out = DenseMat.newOrCheck[T](_dims, oldmat, GUID, a.hashCode, opv.##)
    Mat.nflops += length
    val aa = new Array[T](1)
    aa(0) = a
    opv(_data, 0, 1, aa, 0, 0, out._data, 0, 1, length)    
    out
  }

  // TODO
  def ggReduceOp(dim0:Int, op1:(T) => T, op2:(T,T) => T, oldmat:Mat):DenseMat[T] = {
    var dim = if (nrows == 1 && dim0 == 0) 2 else math.max(1, dim0)
    if (dim == 1) {
      val out = DenseMat.newOrCheck[T](1, ncols, oldmat, GUID, 1, op2.##)
      Mat.nflops += length
      var i = 0
      while (i < ncols) { 
        var j = 1
        var acc = op1(_data(i*nrows))
        while (j < nrows) { 
          acc = op2(acc, _data(j+i*nrows))
          j += 1
        }
        out._data(i) = acc
        i += 1
      }
      out
    } else if (dim == 2) { 
      val out = DenseMat.newOrCheck[T](nrows, 1, oldmat, GUID, 2, op2.##)
      Mat.nflops += length
      var j = 0
      while (j < nrows) { 
        out._data(j) = op1(_data(j))
        j += 1
      }
      var i = 1
      while (i < ncols) { 
        var j = 0
        while (j < nrows) { 
          out._data(j) = op2(out._data(j), _data(j+i*nrows))
          j += 1
        }
        i += 1
      }
      out
    } else
      throw new RuntimeException("index must 1 or 2");
  }
  
  // TODO
  def ggOpt2(dim0:Int, op2:(T,T) => Boolean):(DenseMat[T],IMat) = {
    var dim = if (nrows == 1 && dim0 == 0) 2 else math.max(1, dim0)
    if (dim == 1) {
      val out = DenseMat.newOrCheck[T](1, ncols, null, GUID, op2.hashCode)
      val iout = IMat(1, ncols)
      Mat.nflops += length
      var i = 0
      while (i < ncols) { 
        var j = 1
        var acc = _data(i*nrows)
        var iacc = 0
        while (j < nrows) { 
          val v = _data(j+i*nrows)
          if (op2(v, acc)) {
            acc = v
            iacc = j            
          }
          j += 1
        }
        out._data(i) = acc
        iout._data(i) = iacc
        i += 1
      }
      (out, iout)
    } else if (dim == 2) { 
      val out = DenseMat.newOrCheck[T](nrows, 1, null, GUID, op2.hashCode)
      val iout = IMat(nrows, 1)
      Mat.nflops += length
      var j = 0
      while (j < nrows) { 
        out._data(j) = _data(j)
        iout._data(j) = 0
        j += 1
      }
      var i = 1
      while (i < ncols) { 
        var j = 0
        while (j < nrows) { 
          val v = _data(j+i*nrows)
          if (op2(v, out._data(j))) {
          	out._data(j) = v
          	iout._data(j) = i
          }
          j += 1
        }
        i += 1
      }
      (out, iout)
    } else
      throw new RuntimeException("index must 1 or 2");
  }
  
  // TODO
  def ggReduceOpv(dim0:Int, op1:(T) => T, opv:(Array[T],Int,Int,Array[T],Int,Int,Array[T],Int,Int,Int) => T, oldmat:Mat):DenseMat[T] = {
    var dim = if (nrows == 1 && dim0 == 0) 2 else math.max(1, dim0)
    if (dim == 1) {
      val out = DenseMat.newOrCheck[T](1, ncols, oldmat, GUID, 1, opv.hashCode)
      Mat.nflops += length
      var i = 0
      while (i < ncols) { 
        out._data(i) = op1(_data(i*nrows))
        opv(_data, i*nrows+1, 1, out._data, i, 0, out._data, i, 0, nrows-1)
        i += 1
      }
      out
    } else if (dim == 2) { 
      val out = DenseMat.newOrCheck[T](nrows, 1, oldmat, GUID, 2, opv.hashCode)
      Mat.nflops += length
      var j = 0
      while (j < nrows) { 
        out._data(j) = op1(_data(j))
        j += 1
      }
      var i = 1
      while (i < ncols) { 
        opv(_data, i*nrows, 1, out._data, 0, 1, out._data, 0, 1, nrows)
        i += 1
      }
      out
    } else
      throw new RuntimeException("index must 1 or 2");
  }

  // TODO
  def ggReduceAll(dim0:Int, op1:(T) => T, op2:(T,T) => T, oldmat:Mat):DenseMat[T] = {
    var dim = if (nrows == 1 && dim0 == 0) 2 else math.max(1, dim0)
    if (dim == 1) {
      val out = DenseMat.newOrCheck[T](nrows, ncols, oldmat, GUID, 1, op2.hashCode)
      Mat.nflops += length
      var i = 0
      while (i < ncols) { 
        val i0 = i*nrows
        var j = 1
        var acc = op1(_data(i0))
        out._data(i0) = acc
        while (j < nrows) { 
          acc = op2(acc, _data(j+i0))
          out._data(j+i0) = acc
          j += 1
        }
        i += 1
      }
      out
    } else if (dim == 2) { 
      val out = DenseMat.newOrCheck[T](nrows, ncols, oldmat, GUID, 2, op2.hashCode)
      Mat.nflops += length
      var j = 0
      while (j < nrows) { 
        out._data(j) = op1(_data(j))
        j += 1
      }
      var i = 1
      while (i < ncols) { 
        val i0 = i*nrows
        var j = 0
        while (j < nrows) { 
          out._data(j+i0) = op2(out._data(j+i0-nrows), _data(j+i0))
          j += 1
        }
        i += 1
      }
      out
    } else
      throw new RuntimeException("index must 1 or 2")  
  }
  
  // TODO
  def ggReduceAllv(dim0:Int, opv:(Array[T],Int,Int,Array[T],Int,Int,Array[T],Int,Int,Int) => T, oldmat:Mat):DenseMat[T] = {
    var dim = if (nrows == 1 && dim0 == 0) 2 else math.max(1, dim0)
    if (dim == 1) {
      val out = DenseMat.newOrCheck[T](nrows, ncols, oldmat, GUID, 1, opv.hashCode)
      Mat.nflops += length
      var i = 0
      while (i < ncols) { 
        val i0 = i*nrows
        out._data(i0) = _data(i0)
        opv(_data, i0+1, 1, out._data, i0, 1, out._data, i0+1, 1, nrows-1)
        i += 1
      }
      out
    } else if (dim == 2) { 
      val out = DenseMat.newOrCheck[T](nrows, ncols, oldmat, GUID, 2, opv.hashCode)
      Mat.nflops += length
      var j = 0
      while (j < nrows) { 
        out._data(j) = _data(j)
        j += 1
      }
      var i = 1
      while (i < ncols) { 
        val i0 = i*nrows
        opv(_data, i0, 1, out._data, i0-nrows, 1, out._data, i0, 1, nrows)
        i += 1
      }
      out
    } else
      throw new RuntimeException("index must 1 or 2")  
  }
    
  /**
   * Performs the Hadamard (element-wise) product between this matrix and '''a''', then adds the elements
   * together into a single double.
   * 
   * Throws a RuntimeException if the dimensions are incompatible.
   * 
   * Example:
   * {{{
   * scala> a
   * res8: BIDMat.IMat =
   *    1   2   3
   *    4   5   6
   *    7   8   9
   * 
   * scala> val b = IMat(ones(3,3))
   * b: BIDMat.IMat =
   *    1   1   1
   *    1   1   1
   *    1   1   1
   * 
   * scala> a.ddot(b)
   * res10: Double = 45.0
   * }}}
   * 
   * @param a A matrix with the same dimensions and compatible type as this matrix.
   */
  def ddot (a : DenseMat[T])(implicit numeric:Numeric[T]):Double = 
  	if (nrows != a.nrows || ncols != a.ncols) {
  		throw new RuntimeException("dot dims not compatible")
  	} else {
  		Mat.nflops += 2 * length
  		var v = 0.0
  		var i = 0
  		while (i < length){
  			v += numeric.toDouble(numeric.times(_data(i),a._data(i)))
  			i += 1
  		}
  		v
  	}
  
  // TODO
  def gdot (a : DenseMat[T], oldmat:Mat)(implicit numeric:Numeric[T]):DenseMat[T] = {
  	if (nrows != a.nrows || ncols != a.ncols) {
      throw new RuntimeException("dot dims not compatible")
  	} else {
  	  val out = DenseMat.newOrCheck[T](1, ncols, oldmat, GUID, a.GUID, "gdot".hashCode)
  	  Mat.nflops += 2 * length
  	  var i = 0
  	  while (i < ncols){
  	    val ix = i*nrows
  	    var j = 0
  	    var sum = numeric.zero
  	    while (j < nrows) {
  	    	sum = numeric.plus(sum, numeric.times(_data(j+ix),a._data(j+ix)))
  	    	j += 1
  	    }
  	    out._data(i) = sum
  	  	i += 1
  	  }
  	  out
  	}
  }
 
  // TODO
   def gdotr (a : DenseMat[T], oldmat:Mat)(implicit numeric:Numeric[T]):DenseMat[T] = 
  	if (nrows != a.nrows || ncols != a.ncols) {
  		throw new RuntimeException("dotr dims not compatible")
  	} else {
  		val out = DenseMat.newOrCheck[T](nrows, 1, oldmat, GUID, a.GUID, "gdotr".##)
  		Mat.nflops += 2 * length
  		var i = 0
  		while (i < ncols){
  		  val ix = i*nrows
  		  var j = 0
  		  while (j < nrows) {
  		  	out._data(j) = numeric.plus(out._data(j), numeric.times(_data(j+ix),a._data(j+ix)))
  		  	j += 1
  		  }
  			i += 1
  		}
  		out
  	}
 
  /** 
   * Creates a diagonal, square matrix with this vector's elements in the diagonal. Note that while this will
   * return a correct matrix, but will be printed as an empty matrix on the command line, so it's recommended
   * to use mkdiag(m) instead of this (which is m.mkdiag).
   * 
   * Throws exception if applied to a non-vector matrix.
   * 
   * Example:
   * {{{
   * scala> val a = (1 on 2 on 3).mkdiag
   * a: BIDMat.DenseMat[Int] =
   * 
   * 
   * 
   * 
   * scala> a(1,1)
   * res2: Int = 2
   * 
   * scala> a(1,2)
   * res3: Int = 0
   * }}}
   * 
   * TODO I suggest making this package-protected. ~Daniel Seita
   */
  def mkdiag = {
    if (math.min(nrows, ncols) > 1) {
      throw new RuntimeException("mkdiag needs a vector input")
    }
    val n = math.max(nrows, ncols)
    val out = DenseMat.newOrCheck[T](n, n, null, GUID, "mkdiag".hashCode)
    var i = 0
    while (i < n) {
      out._data(i*(n+1)) = _data(i)
      i += 1
    }
    out
  }
  
  /** 
   * Gets the leading diagonal of this matrix as a vector. Again, like m.mkdiag, m.getdiag will return a
   * seemingly empty matrix on the command line, but it holds the correct elements. Use getdiag(m) for most
   * purposes instead of this method.
   * 
   * Example:
   * {{{
   * scala> val a = 1\2\3 on 4\5\6 on 7\8\9
   * a: BIDMat.IMat =
   *    1   2   3
   *    4   5   6
   *    7   8   9
   * 
   * scala> a.getdiag
   * res4: BIDMat.DenseMat[Int] =
   * 
   * 
   * 
   * 
   * scala> a(0)
   * res5: Int = 1
   * 
   * scala> a(1)
   * res6: Int = 4
   * 
   * scala> a(2)
   * res7: Int = 7
   * }}}
   * 
   * TODO I suggest making this package-protected. ~Daniel Seita
   */
  def getdiag = {
    val n = math.min(nrows, ncols)
    val out = DenseMat.newOrCheck[T](n, 1, null, GUID, "getdiag".hashCode)
    var i = 0
    while (i < n) {
      out._data(i) = _data(i*(nrows+1))
      i += 1
    }
    out
  }
 
}

object DenseMat {
  
  // TODO
  def vecCmp[@specialized(Double, Float, Int, Byte, Long) T](xmap:Array[T])(a:Array[T], a0:Int, ainc:Int, b:Array[T], b0:Int, binc:Int, c:Array[T], c0:Int, cinc:Int, n:Int)
  (implicit numeric:Numeric[T]):T = {
    var ai = a0; var bi = b0; var ci = c0; var cend = c0 + n
    while (ci < cend) {
      val indx = numeric.compare(a(ai), b(bi));  c(ci) = xmap(indx+1); ai += ainc; bi += binc;  ci += cinc
    }
    numeric.zero
  }

  // TODO
  def getInds(ii:IMat, n:Int):(Int)=>Int = {
    var inds:Array[Int] = null
    val off = Mat.oneBased
    ii match {
      case aaa:MatrixWildcard => {
        (x:Int)=>x
      }
      case _ => {
      	(i:Int)	=> {
      		val ind = ii._data(i) - off
          if (ind < 0 || ind >= n) {
            throw new RuntimeException("index out of range "+(ind+off)+" vs "+n)
          } 
      		ind
      	}
      }
    }
  }
  
  // TODO
  def getSInds(in:Seq[Int], n:Int):Array[Int] = {
    var inds:Array[Int] = new Array[Int](math.min(in.length,n))
    val off = Mat.oneBased
    var i = 0
    while (i < in.length) {
    	val ind = in(i) - off
    	if (ind < 0 || ind >= n) {
    		throw new RuntimeException("index out of range "+(ind+off)+" vs "+n)
    	} 
    	i += 1
    }
  inds
  }
   
  // TODO
  def genSort[@specialized(Double, Float, Int, Byte, Long) T](a:Array[T],from:Int,to:Int):Unit = { 
    a match { 
      case aa:Array[Double] => { 
        Arrays.sort(aa, from, to)
      }
      case aa:Array[Float] => { 
        Arrays.sort(aa, from, to)
      }
      case aa:Array[Int] => { 
        Arrays.sort(aa, from, to)
      }
      case aa:Array[Long] => { 
        Arrays.sort(aa, from, to)
      }
      case aa:Array[Byte] => { 
        Arrays.sort(aa, from, to)
      }
    }
  }
  
  // TODO
  def genSort[@specialized(Double, Float, Int, Byte, Long) T](a:Array[T]):Unit = { 
  	genSort(a, 0, a.size)
  }
  
  // TODO
  def reverse[@specialized(Double, Float, Int, Byte, Long) T](a:Array[T],from:Int,to:Int) = {
  	var i = 0
  	var n = to - from
  	while (2*i < n-1) {
  		val tmp = a(i+from)
  		a(i+from) = a(to-i-1)
  		a(to-i-1) = tmp
  		i += 1
  	}
  }
  
  // TODO
  def reverse[@specialized(Double, Float, Int, Byte, Long) T](a:Array[T]):Unit = { 
  	reverse(a, 0, a.size)
  }

  // TODO
  def sort[@specialized(Double, Float, Int, Byte, Long) T](a:DenseMat[T], ik0:Int, asc:Boolean)
  (implicit classTag:ClassTag[T], ordering:Ordering[T]):DenseMat[T] = {
    import BIDMat.Sorting._
    val out = DenseMat.newOrCheck(a.nrows, a.ncols, null, a.GUID, ik0, "DenseMat.sort".hashCode)
    var ik = ik0
    if (ik0 == 0) {
      if (a.nrows == 1) {
        ik = 2
      } else {
        ik = 1
      }
    }    
    if (a.nrows == 1 || a.ncols == 1) {
      System.arraycopy(a._data, 0, out._data, 0, a.length)
      genSort(out._data)
      if (!asc) {
      	reverse(out._data)
      }
      out
    } else if (ik == 1) {
      val thiscol = new Array[T](a.nrows)
      var i = 0
      while (i < a.ncols) {
        var j = 0
        while (j < a.nrows) {
          thiscol(j) = a._data(j+i*a.nrows)
          j += 1
        }
        genSort(thiscol)
        j = 0
        if (asc) {
        	while (j < a.nrows) {
        		out._data(j+i*a.nrows) = thiscol(j)
        		j += 1
        	}
        } else {
          while (j < a.nrows) {
        		out._data(j+i*a.nrows) = thiscol(a.nrows-j-1)
        		j += 1
        	}
        }
        i += 1
      }    
      out
    } else {
      val thisrow = new Array[T](a.ncols)
      var i = 0
      while (i < a.nrows) {
        var j = 0
        while (j < a.ncols) {
          thisrow(j) = a._data(i+j*a.nrows)
          j += 1
        }
        genSort(thisrow)
        j = 0
        if (asc) {
        	while (j < a.ncols) {
        		out._data(i+j*out.nrows) = thisrow(j)
        		j += 1
        	}
        } else {
        	while (j < a.ncols) {
        		out._data(i+j*out.nrows) = thisrow(a.ncols-j-1)
        		j += 1
        	}
        }
        i += 1
      }     
      out
    }
  }
  
  // TODO
  class MyComparator[@specialized(Double, Float, Int, Byte, Long) T](a:Array[T])
  	(implicit ordering:Ordering[T]) extends java.util.Comparator[Int] {
      def compare(ii:Int, jj:Int):Int = {
      val c0 = ordering.compare(a(ii), a(jj))
      if (c0 != 0) {
        c0
      } else {
        ii compare jj
      }      
    }
  }
  
  // TODO
  def sort2[@specialized(Double, Float, Int, Byte, Long) T](a:DenseMat[T], asc:Boolean)
  (implicit classTag:ClassTag[T], ord:Ordering[T]): (DenseMat[T], IMat) = 
    if (a.nrows == 1) {
      sort2(a, 2, asc, null, null)
    } else {
      sort2(a, 1, asc, null, null)
    }
   
  // TODO
  def sort2[@specialized(Double, Float, Int, Byte, Long) T](a:DenseMat[T], ik:Int, asc:Boolean)
  (implicit classTag:ClassTag[T], ord:Ordering[T]):(DenseMat[T], IMat) = sort2(a, ik, asc, null, null)

  // TODO
  def sort2[@specialized(Double, Float, Int, Byte, Long) T](a:DenseMat[T], ik:Int, asc:Boolean, odmat:Mat, oimat:Mat)
  (implicit classTag:ClassTag[T], ord:Ordering[T]):(DenseMat[T], IMat) = {
    import BIDMat.Sorting._
    val out = DenseMat.newOrCheck[T](a.nrows, a.ncols, odmat, a.GUID, ik, "sort2_1".hashCode)
    val iout = IMat.newOrCheckIMat(a.nrows, a.ncols, oimat, a.GUID, ik, "sort2_2".hashCode)
    if (ik == 1) {
      var i = 0
      while (i < a.ncols) {
        var j = 0
        while (j < a.nrows) {
        	iout._data(j+i*a.nrows) = j
        	out._data(j+i*a.nrows) = a._data(j+i*a.nrows)
        	j += 1
        }
        i += 1
      }
      i = 0
      while (i < a.ncols) {
      	if (asc) {
      		quickSort2(out._data, iout._data, i*a.nrows, (i+1)*a.nrows, 1)
      	} else {
      		quickSort2(out._data, iout._data, (i+1)*a.nrows-1, i*a.nrows-1, -1)       
      	}
      	i += 1
      } 
      (out, iout)
    } else {
      val vcols = new Array[T](a.ncols)
      val icols = new Array[Int](a.ncols)
      var i = 0
      while (i < a.nrows) {
        var j = 0
        while (j < a.ncols) {
          vcols(j) = a._data(i + j*a.nrows)
          icols(j) = j
          j += 1
        }
        if (asc) {
          quickSort2(vcols, icols, 0, icols.length, 1)
        } else {
          quickSort2(vcols, icols, icols.length-1, -1, -1)      
        }
        j = 0
        while (j < a.ncols) {
          out._data(i+j*out.nrows) = vcols(j)
          iout._data(i+j*iout.nrows) = icols(j)
          j += 1
        }
        i += 1
      }     
      (out, iout)
    }
  }
  
  // TODO
  def lexcomp[T](a:DenseMat[T], out:IMat)(implicit ordering:Ordering[T]):(Int, Int) => Int = {
  	val aa = a._data
  	val nr = a.nrows
  	val ii = out._data
  	(i:Int, j:Int) => {
  		val ip = ii(i)
  		val jp = ii(j)
  		var c0 = 0
  		var k = 0
  		while (k < a.ncols && c0 == 0) {
  			c0 = ordering.compare(aa(ip+k*nr), aa(jp+k*nr))
  			k += 1
  		}
  		if (c0 != 0) {
  			c0
  		} else {
  			ip compare jp
  		}
  	}
  }
   
  // TODO
  def isortlex[@specialized(Double, Float, Int, Byte, Long) T](a:DenseMat[T], asc:Boolean)(implicit ordering:Ordering[T]):IMat = {
  	val out = IMat.newOrCheckIMat(a.nrows, 1, null, a.GUID, "sortlex".hashCode)
  	val compp = lexcomp(a, out)
  	_isortlex(a, asc, out, compp)
  }
  
  // TODO
  def _isortlex[@specialized(Double, Float, Int, Byte, Long) T](a:DenseMat[T], asc:Boolean, out:IMat, compp:(Int, Int)=>Int)(implicit ordering:Ordering[T]):IMat = {
    import BIDMat.Sorting._
    val ii = out._data
    val aa = a._data
    val nr = a.nrows
    var i = 0
    while (i < a.nrows) {
      out._data(i) = i
      i += 1
    }
 
  // TODO
  def swap(i:Int, j:Int):Unit = {
      val tmp = ii(i)
      ii(i) = ii(j)
      ii(j) = tmp
    }
    if (asc) {
      quickSort(compp, swap, 0, a.nrows)
    } else {
      quickSort((i:Int,j:Int)=>compp(j,i), swap, 0, a.nrows)
    }
    out
  }
  
  // TODO
  def unique2[@specialized(Double, Float, Int, Long) T](a:DenseMat[T])
  (implicit manifest:Manifest[T], numeric:Numeric[T],  ord:Ordering[T]):(IMat, IMat) = {
    val (vss, iss) = sort2(a, true)  
    val iptrs = IMat.newOrCheckIMat(a.length, 1, null, a.GUID, "unique2".hashCode)
    var lastpos = 0
    iptrs._data(iss._data(0)) = lastpos
    var i = 1
    while (i < iss.length) {
      if (vss._data(i-1) !=  vss._data(i)) {
        lastpos += 1
      }
      iptrs._data(iss._data(i)) = lastpos
      i += 1
    }
    val bptrs = IMat.newOrCheckIMat(lastpos+1, 1, null, a.GUID, "unique2_2".hashCode)
    i = iss.length
    while (i > 0) {
      bptrs._data(iptrs._data(i-1)) = i-1
      i = i - 1
    }
    (bptrs, iptrs)    
  } 
  
  // TODO
  def uniquerows2[@specialized(Double, Float, Int, Long) T](a:DenseMat[T])(implicit ordering:Ordering[T]):(IMat, IMat) = {
    val iss = isortlex(a, true)
    def compeq(i:Int, j:Int):Boolean = {
      var k:Int = 0;
      while (k < a.ncols && ordering.equiv(a.gapply(i,k):T, a.gapply(j,k):T)) {
        k += 1
      }
      if (k == a.ncols) true
      else false
    }
    val iptrs = IMat.newOrCheckIMat(a.nrows, 1, null, a.GUID, "uniquerows2".hashCode)
    var lastpos = 0
    iptrs._data(iss._data(0)) = lastpos
    var i = 1
    while (i < iss.length) {
      if (!compeq(iss._data(i-1), iss._data(i))) {
        lastpos += 1
      }
      iptrs._data(iss._data(i)) = lastpos
      i += 1
    }
    val bptrs = IMat.newOrCheckIMat(lastpos+1, 1, null, a.GUID, "uniquerows2_2".hashCode)
    i = iss.length
    while (i > 0) {
      bptrs._data(iptrs._data(i-1)) = i-1
      i = i - 1
    }
    (bptrs, iptrs)    
  }    
  
  // TODO
  def maxelem(ii:IMat):Int = {
    var max0 = 0;
    var i = 0;
    while (i < ii.length) {
    	max0 = math.max(max0, ii._data(i));
    	i += 1;
    }
    max0+1;
  }
  
  // TODO
  def maxcol(ii:IMat, icol:Int):Int = {
    var max0 = 0;
    var i = 0;
    val coloff = icol * ii.nrows;
    while (i < ii.nrows) {
    	max0 = math.max(max0, ii._data(i + coloff));
    	i += 1;
    }
    max0+1;
  }
  
  // TODO
  def accum[@specialized(Double, Float, Int, Long) T](inds:IMat, vals:DenseMat[T], nr0:Int, nc0:Int)
  (implicit numeric:Numeric[T], classTag:ClassTag[T]):DenseMat[T] = { 
 //   if (inds.ncols > 2 || (vals.length > 1 && (inds.nrows != vals.nrows)))
      
  	if (math.min(inds.nrows, inds.ncols) == 1) { // vector case
  	  if (vals.length > 1 && (inds.ncols != vals.ncols || inds.nrows != vals.nrows)) {
  	  	throw new RuntimeException("accum: mismatch in array dimensions")
  	  } 
  	  val colvec = (inds.nrows > inds.ncols);
  	  val nr = if (!colvec) 1 else if (nr0 > 0) nr0 else maxelem(inds);
  	  val nc = if (colvec) 1 else if (nc0 > 0) nc0 else maxelem(inds);
  	  val out = DenseMat.newOrCheck(nr, nc, null, inds.GUID, vals.GUID, "accum".hashCode)
  	  out.clear
  	  		Mat.nflops += inds.length
  	  		var i = 0;
  	  if (vals.length > 1) {
  	  	while (i < inds.length) { 
  	  		out._data(inds._data(i)) = numeric.plus(out._data(inds._data(i)), vals._data(i));
  	  		i += 1;
  	  	}
  	  } else {
  	  	val v = vals._data(0);
  	  	while (i < inds.length) { 
  	  		out._data(inds._data(i)) = numeric.plus(out._data(inds._data(i)), v);
  	  		i += 1;
  	  	}
  	  }
  	  out
  	} else { 
  		Mat.nflops += 3L*inds.nrows;
  		val nr = if (nr0 > 0) nr0 else maxcol(inds, 0);
  		val nc = if (nc0 > 0) nc0 else maxcol(inds, 1);
  		val out = DenseMat.newOrCheck(nr, nc, null, inds.GUID, vals.GUID, "accum".hashCode); 
  		out.clear
  		var i = 0;
  		if (vals.length > 1) {      // Non-scalar case
  			if (inds.nrows != vals.nrows) {
  				throw new RuntimeException("accum: mismatch in array dimensions")
  			}
  			while (i < inds.nrows) { 
  				if (inds._data(i) >= nr || inds._data(i+inds.nrows) >= nc)
  					throw new RuntimeException("indices out of bounds "+inds._data(i)+" "+inds._data(i+inds.nrows))
  				val indx = inds._data(i) + nr*inds._data(i+inds.nrows)
  				out._data(indx) = numeric.plus(out._data(indx), vals._data(i))
  				i += 1
  			}
  		} else {
  			while (i < inds.nrows) { 
  				if (inds._data(i) >= nr || inds._data(i+inds.nrows) >= nc)
  					throw new RuntimeException("indices out of bounds "+inds._data(i)+" "+inds._data(i+inds.nrows))
  				val v = vals._data(0);
  				val indx = inds._data(i) + nr*inds._data(i+inds.nrows)
  						out._data(indx) = numeric.plus(out._data(indx), v)
  						i += 1
  			}
  		}
  		out
  	}
  }

  // TODO
  def newOrCheck[T](nr:Int, nc:Int, oldmat:Mat)
  (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (oldmat.asInstanceOf[AnyRef] == null || (oldmat.nrows == 0 && oldmat.ncols == 0)) {
      new DenseMat[T](nr, nc)
    } else {
      val omat = oldmat.asInstanceOf[DenseMat[T]]
      if (oldmat.nrows != nr || oldmat.ncols != nc) {
        if (nr*nc <= omat._data.size) {
          new DenseMat[T](nr, nc, omat._data)
        } else {
        	new DenseMat[T](nr, nc)
        }
      } else {
        omat
      }
    }
  }
  
  def newOrCheck[T](dims:Array[Int], out:Mat)
  (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (out.asInstanceOf[AnyRef] == null) {
      new DenseMat[T](dims);
    } else {
      val omat = out.asInstanceOf[DenseMat[T]];
      if (omat._dims.sameElements(dims)) {
        omat; 
      } else {
        if (dims.reduce(_*_) <= omat._data.size) {
          new DenseMat[T](dims, omat._data)
        } else {
        	new DenseMat[T](dims)
        }
      } 
    }
  }
  
  // TODO
  def newOrCheck[T](nr:Int, nc:Int, outmat:Mat, matGuid:Long, opHash:Int)
    (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (outmat.asInstanceOf[AnyRef] != null || !Mat.useCache) {
      newOrCheck(nr, nc, outmat)
    } else {
      val key = (matGuid, opHash)
      val res = Mat.cache2(key)
      if (res != null) {
      	newOrCheck(nr, nc, res)
      } else {
        val omat = newOrCheck(nr, nc, null)
        Mat.cache2put(key, omat)
        omat
      }
    }
  }
  
  def newOrCheck[T](dims:Array[Int], outmat:Mat, matGuid:Long, opHash:Int)
    (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (outmat.asInstanceOf[AnyRef] != null || !Mat.useCache) {
      newOrCheck(dims, outmat)
    } else {
      val key = (matGuid, opHash)
      val res = Mat.cache2(key)
      if (res != null) {
      	newOrCheck(dims, res)
      } else {
        val omat = newOrCheck(dims, null)
        Mat.cache2put(key, omat)
        omat
      }
    }
  }
  
  // TODO
  def newOrCheck[T](nr:Int, nc:Int, outmat:Mat, guid1:Long, guid2:Long, opHash:Int)
  (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (outmat.asInstanceOf[AnyRef] != null || !Mat.useCache) {
      newOrCheck(nr, nc, outmat)
    } else {
      val key = (guid1, guid2, opHash)
      val res = Mat.cache3(key)
      if (res != null) {
      	newOrCheck(nr, nc, res)
      } else {
        val omat = newOrCheck(nr, nc, null)
        Mat.cache3put(key, omat)
        omat
      }
    }
  }
  
  def newOrCheck[T](dims:Array[Int], outmat:Mat, guid1:Long, guid2:Long, opHash:Int)
  (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (outmat.asInstanceOf[AnyRef] != null || !Mat.useCache) {
      newOrCheck(dims, outmat)
    } else {
      val key = (guid1, guid2, opHash)
      val res = Mat.cache3(key)
      if (res != null) {
      	newOrCheck(dims, res)
      } else {
        val omat = newOrCheck(dims, null)
        Mat.cache3put(key, omat)
        omat
      }
    }
  }
    
  // TODO
  def newOrCheck[T](nr:Int, nc:Int, outmat:Mat, guid1:Long, guid2:Long, guid3:Long, opHash:Int)
  (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (outmat.asInstanceOf[AnyRef] != null || !Mat.useCache) {
      newOrCheck(nr, nc, outmat)
    } else {
      val key = (guid1, guid2, guid3, opHash)
      val res = Mat.cache4(key)
      if (res != null) {
      	newOrCheck(nr, nc, res)
      } else {
        val omat = newOrCheck(nr, nc, null)
        Mat.cache4put(key, omat)
        omat
      }
    }
  }
  
  def newOrCheck[T](dims:Array[Int], outmat:Mat, guid1:Long, guid2:Long, guid3:Long, opHash:Int)
  (implicit classTag:ClassTag[T]):DenseMat[T] = {
    if (outmat.asInstanceOf[AnyRef] != null || !Mat.useCache) {
      newOrCheck(dims, outmat)
    } else {
      val key = (guid1, guid2, guid3, opHash)
      val res = Mat.cache4(key)
      if (res != null) {
      	newOrCheck(dims, res)
      } else {
        val omat = newOrCheck(dims, null)
        Mat.cache4put(key, omat)
        omat
      }
    }
  }

}

  // TODO
trait MatrixWildcard extends Mat

