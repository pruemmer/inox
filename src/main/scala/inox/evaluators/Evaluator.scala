/* Copyright 2009-2016 EPFL, Lausanne */

package inox
package evaluators

object optIgnoreContracts extends FlagOptionDef("ignorecontracts", false)

trait Evaluator {
  val program: Program
  val options: Options
  import program.trees._

  /** The type of value that this [[Evaluator]] calculates
    * Typically, it will be [[ast.Expressions.Expr Expr]] for deterministic evaluators, and
    * `Stream[[ast.Expressions.Expr Expr]]` for non-deterministic ones.
    */
  type Value

  type EvaluationResult = EvaluationResults.Result[Value]

  /** Evaluates an expression, using `model` as a valuation function for the free variables. */
  def eval(expr: Expr, model: Map[ValDef, Expr]) : EvaluationResult

  /** Evaluates a ground expression. */
  final def eval(expr: Expr) : EvaluationResult = eval(expr, Map.empty)

  /** Compiles an expression into a function, where the arguments are the free variables in the expression.
    * `argorder` specifies in which order the arguments should be passed.
    * The default implementation uses the evaluation function each time, but evaluators are free
    * to (and encouraged to) apply any specialization.
    */
  def compile(expr: Expr, args: Seq[ValDef]) : Option[Map[ValDef, Expr] => EvaluationResult] = Some(
    (model: Map[ValDef, Expr]) => if(args.exists(arg => !model.isDefinedAt(arg))) {
      EvaluationResults.EvaluatorError("Wrong number of arguments for evaluation.")
    } else {
      eval (expr, model)
    }
  )
}

trait DeterministicEvaluator extends Evaluator {
  type Value = program.trees.Expr
}

