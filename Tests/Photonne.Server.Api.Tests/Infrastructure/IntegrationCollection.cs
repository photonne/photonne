namespace Photonne.Server.Api.Tests.Infrastructure;

[CollectionDefinition(Name)]
public sealed class IntegrationCollection : ICollectionFixture<PhotonneApiFactory>
{
    public const string Name = "Integration";
}
