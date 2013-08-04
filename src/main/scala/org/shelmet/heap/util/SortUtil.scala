package org.shelmet.heap.util

object SortUtil {

  def sortByFn[A](cmpFn1: (A, A) => Int,cmpFns: ((A, A) => Int) *) : (A,A) => Boolean = {
    val cmpFnsV = (cmpFn1 :: cmpFns.toList).toVector

    def sortFn(l : A,r : A) : Boolean = {
      var i = 1
      var res = cmpFnsV(0)(l,r)
      while(res == 0 && i < cmpFnsV.length) {
        res = cmpFnsV(i)(l,r)
        i += 1
      }
      res < 0
    }
    sortFn
  }
}
