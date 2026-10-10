using System.Linq.Expressions;

namespace Photonne.Server.Api.Shared.Services.SmartAlbums;

/// <summary>
/// Combines <see cref="Expression{TDelegate}"/> predicates with boolean
/// operators while keeping a single lambda parameter, so the result stays
/// translatable by EF Core (chained <c>.Where</c> only gives AND; smart-album
/// rules need OR and NOT too — see docs/smart-albums/rule-schema.md).
/// </summary>
public static class PredicateBuilder
{
    public static Expression<Func<T, bool>> True<T>() => _ => true;
    public static Expression<Func<T, bool>> False<T>() => _ => false;

    public static Expression<Func<T, bool>> And<T>(
        this Expression<Func<T, bool>> left, Expression<Func<T, bool>> right) =>
        Combine(left, right, Expression.AndAlso);

    public static Expression<Func<T, bool>> Or<T>(
        this Expression<Func<T, bool>> left, Expression<Func<T, bool>> right) =>
        Combine(left, right, Expression.OrElse);

    public static Expression<Func<T, bool>> Not<T>(this Expression<Func<T, bool>> expr) =>
        Expression.Lambda<Func<T, bool>>(Expression.Not(expr.Body), expr.Parameters);

    private static Expression<Func<T, bool>> Combine<T>(
        Expression<Func<T, bool>> left,
        Expression<Func<T, bool>> right,
        Func<Expression, Expression, BinaryExpression> op)
    {
        var param = left.Parameters[0];
        // Rebind the right lambda onto the left's parameter so both bodies share
        // one parameter node (EF requires a single ParameterExpression instance).
        var rebasedRight = new ReplaceParameterVisitor(right.Parameters[0], param).Visit(right.Body)!;
        return Expression.Lambda<Func<T, bool>>(op(left.Body, rebasedRight), param);
    }

    private sealed class ReplaceParameterVisitor : ExpressionVisitor
    {
        private readonly ParameterExpression _from;
        private readonly ParameterExpression _to;

        public ReplaceParameterVisitor(ParameterExpression from, ParameterExpression to)
        {
            _from = from;
            _to = to;
        }

        protected override Expression VisitParameter(ParameterExpression node) =>
            node == _from ? _to : base.VisitParameter(node);
    }
}
