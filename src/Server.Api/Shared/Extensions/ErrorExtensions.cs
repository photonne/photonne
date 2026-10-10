using ErrorOr;

namespace Photonne.Server.Api.Shared.Extensions;

public static class ErrorExtensions
{
    public static IResult ToProblem(this List<Error> errors, int? statusCode = null)
    {
        return errors.Count switch
        {
            <= 0 => Results.Problem(statusCode: statusCode),
            1 => HandleSingleError(errors.First(), statusCode),
            > 1 => HandleMultipleErrors(errors, statusCode)
        };
    }

    private static int GetDefaultStatusCode(Error error) => error.NumericType switch
    {
        (int)ErrorType.Failure => StatusCodes.Status503ServiceUnavailable,
        (int)ErrorType.Unexpected => StatusCodes.Status500InternalServerError,
        (int)ErrorType.Validation => StatusCodes.Status400BadRequest,
        (int)ErrorType.Conflict => StatusCodes.Status409Conflict,
        (int)ErrorType.NotFound => StatusCodes.Status404NotFound,
        (int)ErrorType.Unauthorized => StatusCodes.Status401Unauthorized,
        (int)ErrorType.Forbidden => StatusCodes.Status403Forbidden,
        _ => StatusCodes.Status500InternalServerError,
    };

    private static IResult HandleMultipleErrors(List<Error> errors, int? statusCode = null) => Results.Problem(
        statusCode: statusCode ?? GetDefaultStatusCode(errors.First()),
        title: "One or more validation errors occurred.",
        extensions: new Dictionary<string, object?>()
        {
            { "errors", errors
                .GroupBy(e => e.Code)
                .ToDictionary(
                    group => group.Key,
                    group => group.Select(e => e.Description).ToList()
                )
            }
        });

    private static IResult HandleSingleError(Error error, int? statusCode = null) => Results.Problem(
        statusCode: statusCode ?? GetDefaultStatusCode(error),
        title: error.Code,
        detail: error.Description);
}