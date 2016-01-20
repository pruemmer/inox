import leon.lazyeval._
import leon.lazyeval.$._
import leon.lang._
import leon.annotation._
import leon.instrumentation._
//import leon.invariant._

object Knapscak {
  sealed abstract class IList {
    def size: BigInt = {
      this match {
        case Cons(_, tail) => 1 + tail.size
        case Nil() => BigInt(0)
      }
    } ensuring(_ >= 0)
  }
  case class Cons(x: BigInt, tail: IList) extends IList
  case class Nil() extends IList

  def depsEval(i: BigInt, items: IList): Boolean = {
    require(i >= 0)
    knapSack(i, items).isCached && // if we have the cached check only along the else branch, we would get a counter-example.
      (if (i <= 0) true
      else {
        depsEval(i - 1, items)
      })
  }

  @traceInduct
  def depsEvalMono(i: BigInt, items: IList, st1: Set[$[BigInt]], st2: Set[$[BigInt]]) = {
    require(i >= 0)
    (st1.subsetOf(st2) && (depsEval(i, items) withState st1)) ==> (depsEval(i, items) withState st2)
  } holds

  @traceInduct
  def depsLem(x: BigInt, y: BigInt, items: IList) = {
    require(x >= 0 && y >= 0)
    (x <= y && depsEval(y, items)) ==> depsEval(x, items)
  } holds

  @invstate
  def maxValue(items: IList, w: BigInt, currList: IList): BigInt = {
    require((w ==0 || (w > 0 && depsEval(w - 1, items))) &&
      // lemma inst
      (currList match {
        case Cons(wi, _) =>
          if (wi <= w && wi > 0) depsLem(w - wi, w - 1, items)
          else true
        case Nil() => true
      }))
    currList match {
      case Cons(wi, tail) =>
        val oldMax = maxValue(items, w, tail)
        if (wi <= w && wi > 0) {
          val choiceVal = wi + knapSack(w - wi, items)
          if (choiceVal >= oldMax)
            choiceVal
          else
            oldMax
        } else oldMax
      case Nil() => BigInt(0)
    }
  } ensuring(_ => time <= 30*currList.size + 20) // interchanging currList and items in the bound will produce a counter-example

  @memoize
  def knapSack(w: BigInt, items: IList): BigInt = {
    require(w >= 0 && (w == 0 || depsEval(w - 1, items)))
    if (w == 0) BigInt(0)
    else {
      maxValue(items, w, items)
    }
  } ensuring(_ => time <= 30*items.size + 25)

  def invoke(i: BigInt, items: IList) = {
    require(i == 0 || (i > 0 && depsEval(i - 1, items)))
    knapSack(i, items)
  } ensuring (res => {
    val in = $.inState[BigInt]
    val out = $.outState[BigInt]
    depsEvalMono(i - 1, items, in, out) &&
      depsEval(i - 1, items) &&
      time <= 30*items.size + 40
  })

  def bottomup(i: BigInt, w: BigInt, items: IList): IList = {
    require(i >= 0 && w >= i &&
      (i == 0 || (i > 0 && depsEval(i - 1, items))))
    val ri = invoke(i, items)
    if (i == w)
      Cons(ri, Nil())
    else {
      Cons(ri, bottomup(i + 1, w, items))
    }
  } ensuring(_ => items.size <= 10 ==> time <= 400 * (w - i + 1))

  def knapSackSol(w: BigInt, items: IList) = {
    require(w >= 0 && items.size <= 10) //  the second requirement is only to keep the bounds linear for z3 to work
    bottomup(0, w, items)
  } ensuring(time <= 400*w + 410)

}
