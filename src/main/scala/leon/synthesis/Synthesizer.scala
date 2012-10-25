package leon
package synthesis

import purescala.Common._
import purescala.Definitions.{Program, FunDef}
import purescala.TreeOps._
import purescala.Trees.{Expr, Not}
import purescala.ScalaPrinter

import Extensions.Solver

import collection.mutable.PriorityQueue

class Synthesizer(val r: Reporter, val solvers: List[Solver]) {
  import r.{error,warning,info,fatalError}

  private[this] var solution: Option[Solution] = None
  private[this] var derivationTree: DerivationTree = _

  var derivationCounter = 1;

  def synthesize(p: Problem, rules: List[Rule]): Solution = {

    val workList = new PriorityQueue[Task]()
    val rootTask = new RootTask(this, p)


    workList += rootTask
    solution = None
    derivationTree = new DerivationTree(rootTask)

    while (!workList.isEmpty && solution.isEmpty) {
      val task = workList.dequeue()

      info("Now handling: "+task.problem)

      val alternatives = rules.flatMap(_.isApplicable(task))

      alternatives.find(_.isSuccess) match {
        case Some(ss) =>
          info(" => Rule "+ss.rule+" succeeded")
          ss.succeeded()
        case None =>
          info(" => Possible Next Steps:")
          alternatives.foreach(t => info(" -   "+t.description))
          workList ++= alternatives.flatMap(_.subTasks)
      }

      // We are stuck
      if (alternatives.isEmpty) {
        val sol = Solution.choose(task.problem)
        warning(" => I give up: "+task.problem+" ⊢  "+sol)
        onTaskSucceeded(task, sol)
      }
    }


    derivationTree.toDotFile("derivation"+derivationCounter+".dot")
    derivationCounter += 1

    solution.getOrElse(Solution.none)
  }

  def onTaskSucceeded(task: Task, solution: Solution) {
    info(" => Solved "+task.problem+" ⊢  "+solution)
    derivationTree.recordSolutionFor(task, solution)

    if (task.parent eq null) {
      info(" SUCCESS!")
      this.solution = Some(solution)
    } else {
      task.parent.subSucceeded(task.problem, solution)
    }
  }

  def solveSAT(phi: Expr): (Option[Boolean], Map[Identifier, Expr]) = {
    for (s <- solvers) {
      s.solveOrGetCounterexample(Not(phi)) match {
        case (Some(true), _) =>
          return (Some(false), Map())
        case (Some(false), model) =>
          return (Some(true), model)
        case _ =>
      }
    }
    (None, Map())
  }

  import purescala.Trees._
  def synthesizeAll(program: Program): List[(Choose, Solution)] = {

    solvers.foreach(_.setProgram(program))

    val rules = Rules.all(this)

    def noop(u:Expr, u2: Expr) = u

    var solutions = List[(Choose, Solution)]()

    def actOnChoose(f: FunDef)(e: Expr, a: Expr): Expr = e match {
      case ch @ Choose(vars, pred) =>
        val xs = vars
        val as = (variablesOf(pred)--xs).toList
        val phi = pred

        info("")
        info("")
        info("In Function "+f.id+":")
        info("-"*80)

        val sol = synthesize(Problem(as, phi, xs), rules)

        solutions = (ch, sol) :: solutions

        info("Scala code:")
        info(ScalaPrinter(simplifyLets(sol.toExpr)))

        a
      case _ =>
        a
    }

    // Look for choose()
    for (f <- program.definedFunctions.sortBy(_.id.toString) if f.body.isDefined) {
      treeCatamorphism(x => x, noop, actOnChoose(f), f.body.get)
    }

    solutions
  }

  def substitueChooses(file: String, sols: List[(Choose, Solution)]) = {
    import scala.io.Source

    val src = Source.fromFile(file)

  }
}
