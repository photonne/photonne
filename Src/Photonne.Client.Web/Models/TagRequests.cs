namespace Photonne.Client.Web.Models;

public class AddTagsRequest
{
    public List<string> Tags { get; set; } = new();
}

public class TagUpdateResponse
{
    public List<string> Tags { get; set; } = new();
}
