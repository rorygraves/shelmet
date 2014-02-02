package no.uib.cipr.matrix.sparse {
  // https://github.com/fommil/matrix-toolkits-java/issues/43
  class LinkedMatrix(size: Int) extends LinkedSparseMatrix(size, size) {
    // this should significantly speed up future lookups
    (0 until size) foreach {i =>
      set(i, i, 1.0)
    }
  }
}
