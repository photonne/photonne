using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Text.Json.Serialization.Metadata;
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
///   * Response fields are required. The server writes every property (null
///     included), so a response object always carries them all; without
///     <c>required</c> a generated client types every field as optional.
///     Only schemas that never appear in a request get it: on a request it
///     would mean "the client must send it", and adding an optional field to
///     a request would then read as a breaking change.
/// </summary>
public static class OpenApiConfiguration
{
    public const string BearerScheme = "Bearer";

    // Carries, from the schema transformer to the document transformer, the
    // properties a type always serializes. Removed before the document is served.
    private const string AlwaysPresentExtension = "x-photonne-always-present";

    public static IServiceCollection AddPhotonneOpenApi(this IServiceCollection services)
        => services.AddOpenApi(options =>
        {
            options.AddDocumentTransformer((document, _, _) =>
            {
                MarkResponseFieldsRequired(document);
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

            options.AddSchemaTransformer((schema, context, _) =>
            {
                if (context.JsonPropertyInfo is null
                    && context.JsonTypeInfo.Kind == JsonTypeInfoKind.Object
                    && schema.Properties is { Count: > 0 })
                {
                    // A property with a conditional ignore ([JsonIgnore(Condition =
                    // WhenWritingNull)], a ShouldSerialize predicate) may be missing.
                    var alwaysPresent = context.JsonTypeInfo.Properties
                        .Where(p => p.Get is not null && p.ShouldSerialize is null && !IsConditionallyIgnored(p))
                        .Select(p => (JsonNode)p.Name)
                        .ToArray();
                    // ASP.NET marks a record's constructor parameters required, ignore
                    // condition or not; a field that can be left out isn't.
                    foreach (var omittable in context.JsonTypeInfo.Properties.Where(IsConditionallyIgnored))
                        schema.Required?.Remove(omittable.Name);

                    schema.Extensions ??= new Dictionary<string, IOpenApiExtension>();
                    schema.Extensions[AlwaysPresentExtension] = new JsonNodeExtension(new JsonArray(alwaysPresent));
                }

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

    private static void MarkResponseFieldsRequired(OpenApiDocument document)
    {
        var schemas = document.Components?.Schemas;
        if (schemas is null) return;

        var inRequests = new HashSet<string>();
        foreach (var operation in document.Paths.Values.SelectMany(p => p.Operations?.Values.AsEnumerable() ?? []))
        {
            foreach (var content in operation.RequestBody?.Content?.Values ?? [])
                CollectReferences(content.Schema, schemas, inRequests);
            foreach (var parameter in operation.Parameters ?? [])
                CollectReferences(parameter.Schema, schemas, inRequests);
        }

        foreach (var (name, schema) in schemas)
        {
            if (schema is not OpenApiSchema target
                || target.Extensions?.Remove(AlwaysPresentExtension, out var extension) != true)
            {
                continue;
            }
            if (inRequests.Contains(name) || extension is not JsonNodeExtension { Node: JsonArray names })
                continue;

            target.Required ??= new HashSet<string>();
            foreach (var property in names.Select(n => n!.GetValue<string>()))
            {
                if (target.Properties?.ContainsKey(property) == true)
                    target.Required.Add(property);
            }
        }
    }

    private static bool IsConditionallyIgnored(JsonPropertyInfo property)
        => property.AttributeProvider?.GetCustomAttributes(typeof(JsonIgnoreAttribute), inherit: true)
            .OfType<JsonIgnoreAttribute>()
            .Any(a => a.Condition != JsonIgnoreCondition.Never) == true;

    /// <summary>Every component schema reachable from <paramref name="schema"/>.</summary>
    private static void CollectReferences(
        IOpenApiSchema? schema,
        IDictionary<string, IOpenApiSchema> components,
        HashSet<string> found)
    {
        switch (schema)
        {
            case null:
                return;
            case OpenApiSchemaReference reference:
                if (reference.Reference.Id is { } id && found.Add(id) && components.TryGetValue(id, out var target))
                    CollectReferences(target, components, found);
                return;
        }

        foreach (var child in (schema.Properties?.Values ?? [])
                     .Concat(schema.OneOf ?? []).Concat(schema.AnyOf ?? []).Concat(schema.AllOf ?? []))
        {
            CollectReferences(child, components, found);
        }
        CollectReferences(schema.Items, components, found);
        CollectReferences(schema.AdditionalProperties, components, found);
    }
}
