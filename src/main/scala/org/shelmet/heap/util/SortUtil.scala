package org.shelmet.heap.util

object SortUtil {

  def sortByFirstThen[X](cmp1 : (X,X) => Int,cmp2: (X,X) => Int) : (X,X) => Boolean = {
    def cmpFn(x1 : X,x2 : X) : Boolean = {
      val diff: Int = cmp1(x1,x2)
      if (diff != 0)
        diff < 0
      else
        cmp2(x1,x2) <0
    }
    cmpFn
  }
}
