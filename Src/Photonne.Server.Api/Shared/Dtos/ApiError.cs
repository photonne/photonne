namespace Photonne.Server.Api.Shared.Dtos;

/// <summary>
/// Body of every 4xx the API answers on purpose. <see cref="Error"/> keeps the
/// <c>{ "error": "…" }</c> shape the published apps already read; <see cref="Code"/>
/// is a stable, machine-readable reason (<c>asset_not_found</c>, …) for clients
/// that need to branch on it instead of on the human text.
///
/// Unexpected failures (500) are not this: they are ProblemDetails written by
/// the exception middleware, with a trace id and no internals.
/// </summary>
public sealed record ApiError(string Error, string Code);
