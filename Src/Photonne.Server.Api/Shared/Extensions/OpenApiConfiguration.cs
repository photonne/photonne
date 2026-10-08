using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.OpenApi;
using Microsoft.OpenApi;

namespace Photonne.Server.Api.Shared.Extensions;

/// <summary>
/// Shapes the OpenAPI document into a contract a client generator can use
/// as is (the web client's TypeScript types come from it):
///   * Numbers are numbers. The web JSON defaults also read numbers from
///     strings, and the generator documents that as <c>integer | string</c>
///     with a regex — true for the server, useless for a typed client.
///   * Authentication is declared: a Bearer scheme, required on every
///     operation that has authorization metadata.
/// </summary>
public static class OpenApiConfiguration
{
    public const string BearerScheme = "Bearer";

    public static IServiceCollection AddPhotonneOpenApi(this IServiceCollection services)
        => services.AddOpenApi(options =>
        {
            options.AddDocumentTransformer((document, _, _) =>
            {
                document.Info.Title = "Photonne API";
                document.Components ??= new OpenApiComponents();
                document.Components.SecuritySchemes ??= new Dictionary<string, IOpenApiSecurityScheme>();
                document.Components.SecuritySchemes[BearerScheme] = new OpenApiSecurityScheme
                {
                    Type = SecuritySchemeType.Http,
                    Scheme = "bearer",
                    BearerFormat = "JWT",
                    Description = "Access token from /api/auth/login or /api/auth/refresh",
                };
                return Task.CompletedTask;
            });

            options.AddOperationTransformer((operation, context, _) =>
            {
                var metadata = context.Description.ActionDescriptor.EndpointMetadata;
                var requiresAuth = metadata.OfType<IAuthorizeData>().Any()
                                   && !metadata.OfType<IAllowAnonymous>().Any();
                if (requiresAuth)
                {
                    operation.Security ??= [];
                    operation.Security.Add(new OpenApiSecurityRequirement
                    {
                        [new OpenApiSecuritySchemeReference(BearerScheme, context.Document)] = [],
                    });
                }
                return Task.CompletedTask;
            });

            options.AddSchemaTransformer((schema, _, _) =>
            {
                if (schema.Type is { } type
                    && (type.HasFlag(JsonSchemaType.Integer) || type.HasFlag(JsonSchemaType.Number))
                    && type.HasFlag(JsonSchemaType.String))
                {
                    schema.Type = type & ~JsonSchemaType.String;
                    schema.Pattern = null;
                }
                return Task.CompletedTask;
            });
        });
}
