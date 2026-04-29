using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public enum MlJobType
{
    FaceRecognition,
    ObjectDetection,
    SceneClassification,
    TextRecognition
}

public enum MlJobStatus
{
    Pending,
    Processing,
    Completed,
    Failed
}

public class AssetMlJob
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;
    
    public MlJobType JobType { get; set; }
    
    public MlJobStatus Status { get; set; } = MlJobStatus.Pending;
    
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    
    public DateTime? StartedAt { get; set; }
    
    public DateTime? CompletedAt { get; set; }
    
    [MaxLength(2000)]
    public string? ErrorMessage { get; set; }
    
    public string? ResultJson { get; set; } // Results in JSON format
}
