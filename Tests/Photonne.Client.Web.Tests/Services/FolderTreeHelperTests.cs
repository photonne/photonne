using Photonne.Client.Web.Models;
using Photonne.Client.Web.Services;
using Xunit;

namespace Photonne.Client.Web.Tests.Services;

public class FolderTreeHelperTests
{
    private static FolderItem Folder(string name, params FolderItem[] children) => new()
    {
        Id = Guid.NewGuid(),
        Name = name,
        SubFolders = children.ToList()
    };

    [Fact]
    public void PathTo_RootFolder_ReturnsSingleElement()
    {
        var root = Folder("Fotos");
        var path = FolderTreeHelper.PathTo(new List<FolderItem> { root }, root.Id);

        Assert.Single(path);
        Assert.Equal(root.Id, path[0].Id);
    }

    [Fact]
    public void PathTo_DeepFolder_ReturnsChainFromRoot()
    {
        var leaf = Folder("Japón");
        var mid = Folder("Viajes", leaf);
        var root = Folder("Fotos", mid);
        var sibling = Folder("Otros");

        var path = FolderTreeHelper.PathTo(new List<FolderItem> { sibling, root }, leaf.Id);

        Assert.Equal(new[] { root.Id, mid.Id, leaf.Id }, path.Select(f => f.Id));
    }

    [Fact]
    public void PathTo_UnknownId_ReturnsEmpty()
    {
        var root = Folder("Fotos", Folder("Viajes"));
        var path = FolderTreeHelper.PathTo(new List<FolderItem> { root }, Guid.NewGuid());

        Assert.Empty(path);
    }

    [Fact]
    public void PathTo_EmptyTree_ReturnsEmpty()
    {
        Assert.Empty(FolderTreeHelper.PathTo(new List<FolderItem>(), Guid.NewGuid()));
    }

    [Fact]
    public void AncestorIds_ExcludesTheFolderItself()
    {
        var leaf = Folder("Japón");
        var mid = Folder("Viajes", leaf);
        var root = Folder("Fotos", mid);

        var ancestors = FolderTreeHelper.AncestorIds(new List<FolderItem> { root }, leaf.Id);

        Assert.Equal(new HashSet<Guid> { root.Id, mid.Id }, ancestors);
    }

    [Fact]
    public void AncestorIds_RootFolder_ReturnsEmpty()
    {
        var root = Folder("Fotos");
        Assert.Empty(FolderTreeHelper.AncestorIds(new List<FolderItem> { root }, root.Id));
    }

    [Fact]
    public void AncestorIds_UnknownId_ReturnsEmpty()
    {
        var root = Folder("Fotos");
        Assert.Empty(FolderTreeHelper.AncestorIds(new List<FolderItem> { root }, Guid.NewGuid()));
    }
}
