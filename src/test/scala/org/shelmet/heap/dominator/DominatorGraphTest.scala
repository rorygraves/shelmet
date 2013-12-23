//package org.shelmet.heap.dominator
//
//import org.shelmet.shared.dominator.{DominatorCalculator, DominatorNode}
//import org.scalatest.FunSuite
//
//object DominatorGraphTest {
//  class TestDomNode(val name : String,upstreamInit : Set[TestDomNode]= Set.empty,
//                    downstreamInit : Set[TestDomNode]=Set.empty) extends DominatorNode[TestDomNode] {
//
//
//    override def toString = name
//
//    var usNodes : Set[TestDomNode] = upstreamInit
//    override def upstreamNodes = usNodes
//
//    def addUpstream(usn : TestDomNode) { usNodes += usn }
//
//    var dsNodes : Set[TestDomNode] = downstreamInit
//    override def downstreamNodes = dsNodes
//
//    def addDownstream(dsn : TestDomNode) { dsNodes += dsn }
//
//
//    override def hashCode = name.hashCode
//    override def equals(other : Any) = other match {
//      case o : TestDomNode if o.name == name => true
//      case _ => false
//    }
//  }
//
//  def splitToPairs(str : String) : Set[(String,String)] = {
//    val items = str.split(',').toSet
//    val pairs = items.map{ pair =>
//      val pairItems = pair.split("->")
//      assert(pairItems.size == 2)
//      (pairItems(0),pairItems(1))
//    }
//
//    pairs
//  }
//  /**
//   * Take a graph description of the form a->b,b->c) and convert it to a set of
//   * test nodes wired correctly
//   * @param desc The description of the graph
//   * @return a map of node name to test node
//   */
//  def createTestSet(desc : String) : Map[String,TestDomNode] = {
//    val pairs = splitToPairs(desc)
//
//    // generate the full set of node names
//    val nodeNames = pairs.flatMap( x => List(x._1,x._2))
//
//
//    println("Nodes = " + nodeNames.size)
//    // create the node map
//    val nodes = nodeNames.map(x => (x,new TestDomNode(x))).toMap
//
//    // now add the linkage information
//    pairs.foreach {
//      case (upstream,downstream) =>
//        val usNode = nodes(upstream)
//        val dsNode = nodes(downstream)
//        usNode.addDownstream(dsNode)
//        dsNode.addUpstream(usNode)
//    }
//    nodes
//  }
//}
//
//class DominatorGraphTest extends FunSuite {
//  import DominatorGraphTest._
//
//
//  def runTest(graphDef : String, expectedDomsDef : String) {
//    val nodes = createTestSet(graphDef)
//    val calc = new DominatorCalculator[TestDomNode]
//    val dominators: Map[TestDomNode, Option[TestDomNode]] = calc.calculateDominators(nodes.values.toSet)
//
//    val expectedDoms = splitToPairs(expectedDomsDef)
//
//    val actualDominatorCount = dominators.values.count(_.isDefined)
//
//    assert(expectedDoms.size === actualDominatorCount,
//      s"No expected dominators (${expectedDoms.size}) does not match actual dominator count (${actualDominatorCount})")
//    expectedDoms.foreach {
//      case (domName,subName) =>
//        val dom = nodes(domName)
//        val sub = nodes(subName)
//        dominators.get(sub) match {
//          case Some(Some(node)) => if(node != dom)
//            fail(s"Dominator of node $subName should be $domName but is different")
//          case _ => fail(s"Node $subName dominator should be $domName, was undefined")
//        }
//    }
//  }
//
//  test("A->B should have A as dominator of B and nothing else") {
//    runTest("A->B","A->B")
//  }
//
//  test("two chains") {
//    runTest("A->B,B->C,D->E,E->F","A->B,B->C,D->E,E->F")
//  }
//
//  // MAT Example
//  // http://help.eclipse.org/indigo/index.jsp?topic=%2Forg.eclipse.mat.ui.help%2Fwelcome.html
//  test("MAT Example dom graph") {
//
//    val nodes = "A->C,B->C,C->D,C->E,D->F,E->G,F->D,F->H,G->H"
//    val doms = "C->D,C->E,D->F,E->G,C->H"
//
//
//    runTest(nodes,doms)
//  }
//
//  test("Simple diamond") {
//
//    val nodes = "A->B,A->C,B->D,C->D"
//    val doms = "A->B,A->C,A->D"
//
//
//    runTest(nodes,doms)
//  }
//
//  test("Large file") {
//    val graphDef = scala.io.Source.fromFile("/tmp/test.txt").getLines().next()
//    println("line.size = " + graphDef.size)
//    val nodes = createTestSet(graphDef)
//    val calc = new DominatorCalculator[TestDomNode]
//    val startTime = System.currentTimeMillis()
//    val dominators: Map[TestDomNode, Option[TestDomNode]] = calc.calculateDominators(nodes.values.toSet)
//
//    val endTime = System.currentTimeMillis()
//    println(s"Took ${endTime-startTime}ms")
//
//
//    val actualDominatorCount = dominators.values.count(_.isDefined)
//
//  }
//}
//
