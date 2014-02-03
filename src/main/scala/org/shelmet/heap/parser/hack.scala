package org.shelmet.heap {

import no.uib.cipr.matrix.sparse.LinkedSparseMatrix

// https://github.com/fommil/matrix-toolkits-java/issues/43
object LinkedMatrix {
  def apply(size: Int): LinkedSparseMatrix = {
    val matrix = new LinkedSparseMatrix(size, size)
    // this should significantly speed up future lookups
    (0 until size) foreach { i =>
      matrix.set(i, i, 1.0)
    }
    matrix
  }
}

}
