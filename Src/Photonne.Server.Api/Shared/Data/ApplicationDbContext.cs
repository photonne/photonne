using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Data;

public class ApplicationDbContext : DbContext
{
    // Reusable converters for UTC <-> timestamp without time zone
    private static readonly ValueConverter<DateTime, DateTime> UtcConverter = new(
        v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
        v => DateTime.SpecifyKind(v, DateTimeKind.Utc));

    private static readonly ValueConverter<DateTime?, DateTime?> NullableUtcConverter = new(
        v => v.HasValue && v.Value.Kind == DateTimeKind.Utc
            ? DateTime.SpecifyKind(v.Value, DateTimeKind.Unspecified)
            : v,
        v => v.HasValue ? DateTime.SpecifyKind(v.Value, DateTimeKind.Utc) : null);

    public ApplicationDbContext(DbContextOptions<ApplicationDbContext> options) : base(options)
    {
    }
    
    public DbSet<Asset> Assets { get; set; }
    public DbSet<AssetExif> AssetExifs { get; set; }
    public DbSet<AssetThumbnail> AssetThumbnails { get; set; }
    public DbSet<AssetTag> AssetTags { get; set; }
    public DbSet<UserTag> UserTags { get; set; }
    public DbSet<AssetUserTag> AssetUserTags { get; set; }
    public DbSet<AssetMlJob> AssetMlJobs { get; set; }
    public DbSet<User> Users { get; set; }
    public DbSet<Folder> Folders { get; set; }
    public DbSet<FolderPermission> FolderPermissions { get; set; }
    public DbSet<Setting> Settings { get; set; }
    public DbSet<Album> Albums { get; set; }
    public DbSet<AlbumAsset> AlbumAssets { get; set; }
    public DbSet<AlbumPermission> AlbumPermissions { get; set; }
    public DbSet<RefreshToken> RefreshTokens { get; set; }
    public DbSet<SharedLink> SharedLinks { get; set; }
    public DbSet<Notification> Notifications { get; set; }
    public DbSet<ExternalLibrary> ExternalLibraries { get; set; }
    public DbSet<ExternalLibraryPermission> ExternalLibraryPermissions { get; set; }
    public DbSet<Face> Faces { get; set; }
    public DbSet<Person> People { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasPostgresExtension("vector");

        // Configure User entity
        modelBuilder.Entity<User>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Username).IsRequired().HasMaxLength(100);
            entity.Property(e => e.Email).IsRequired().HasMaxLength(255);
            entity.Property(e => e.PasswordHash).IsRequired().HasMaxLength(255);
            entity.Property(e => e.FirstName).HasMaxLength(100);
            entity.Property(e => e.LastName).HasMaxLength(100);
            entity.Property(e => e.Role).HasMaxLength(50).HasDefaultValue("User");
            entity.Property(e => e.IsPrimaryAdmin).HasDefaultValue(false);
            entity.HasIndex(e => e.Username).IsUnique();
            entity.HasIndex(e => e.Email).IsUnique();
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.LastLoginAt).HasColumnType("timestamp without time zone").HasConversion(NullableUtcConverter);
        });

        // Configure Folder entity
        modelBuilder.Entity<Folder>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Path).IsRequired().HasMaxLength(1000);
            entity.Property(e => e.Name).IsRequired().HasMaxLength(500);
            entity.HasIndex(e => e.Path).IsUnique();
            entity.HasIndex(e => e.ParentFolderId);

            entity.HasOne(e => e.ParentFolder)
                .WithMany(e => e.SubFolders)
                .HasForeignKey(e => e.ParentFolderId)
                .OnDelete(DeleteBehavior.Restrict);

            entity.HasOne(e => e.ExternalLibrary)
                .WithMany()
                .HasForeignKey(e => e.ExternalLibraryId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasIndex(e => e.ExternalLibraryId);
            
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure FolderPermission entity
        modelBuilder.Entity<FolderPermission>(entity =>
        {
            entity.HasKey(e => e.Id);

            entity.HasOne(e => e.User)
                .WithMany(e => e.FolderPermissions)
                .HasForeignKey(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.Folder)
                .WithMany(e => e.Permissions)
                .HasForeignKey(e => e.FolderId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.GrantedByUser)
                .WithMany()
                .HasForeignKey(e => e.GrantedByUserId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasIndex(e => new { e.UserId, e.FolderId }).IsUnique();
            entity.Property(e => e.GrantedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });
        
        // Configure Asset entity
        modelBuilder.Entity<Asset>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.FileName).IsRequired().HasMaxLength(500);
            entity.Property(e => e.FullPath).IsRequired().HasMaxLength(1000);
            entity.Property(e => e.Checksum).IsRequired().HasMaxLength(64);
            entity.Property(e => e.Extension).IsRequired().HasMaxLength(10);
            entity.Property(e => e.Caption).HasMaxLength(2000);
            entity.Property(e => e.AiDescription).HasMaxLength(2000);
            entity.HasIndex(e => e.FullPath).IsUnique();
            entity.HasIndex(e => e.Checksum);
            entity.HasIndex(e => e.FileName);
            entity.HasIndex(e => e.FolderId);
            entity.HasIndex(e => e.OwnerId);

            entity.HasOne(e => e.Owner)
                .WithMany(u => u.Assets)
                .HasForeignKey(e => e.OwnerId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasOne(e => e.Folder)
                .WithMany(f => f.Assets)
                .HasForeignKey(e => e.FolderId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasOne(e => e.ExternalLibrary)
                .WithMany(l => l.Assets)
                .HasForeignKey(e => e.ExternalLibraryId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasIndex(e => e.ExternalLibraryId);
            entity.HasIndex(e => e.IsFileMissing);

            entity.Property(e => e.FileCreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.FileModifiedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.ScannedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure AssetExif entity
        modelBuilder.Entity<AssetExif>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.CameraMake).HasMaxLength(200);
            entity.Property(e => e.CameraModel).HasMaxLength(200);
            entity.Property(e => e.Description).HasMaxLength(500);
            entity.Property(e => e.Keywords).HasMaxLength(1000);

            entity.HasOne(e => e.Asset)
                .WithOne(a => a.Exif)
                .HasForeignKey<AssetExif>(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.AssetId).IsUnique();
            entity.Property(e => e.DateTimeOriginal).HasColumnType("timestamp without time zone").HasConversion(NullableUtcConverter);
            entity.Property(e => e.ExtractedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure AssetThumbnail entity
        modelBuilder.Entity<AssetThumbnail>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.FilePath).IsRequired().HasMaxLength(1000);
            entity.Property(e => e.Format).IsRequired().HasMaxLength(20);

            entity.HasOne(e => e.Asset)
                .WithMany(a => a.Thumbnails)
                .HasForeignKey(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.AssetId);
            entity.HasIndex(e => new { e.AssetId, e.Size }).IsUnique();
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure AssetTag entity
        modelBuilder.Entity<AssetTag>(entity =>
        {
            entity.HasKey(e => e.Id);

            entity.HasOne(e => e.Asset)
                .WithMany(a => a.Tags)
                .HasForeignKey(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.AssetId);
            entity.HasIndex(e => new { e.AssetId, e.TagType }).IsUnique();
            entity.Property(e => e.DetectedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure UserTag entity
        modelBuilder.Entity<UserTag>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Name).IsRequired().HasMaxLength(80);
            entity.Property(e => e.NormalizedName).IsRequired().HasMaxLength(80);

            entity.HasOne(e => e.Owner)
                .WithMany(u => u.Tags)
                .HasForeignKey(e => e.OwnerId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.OwnerId);
            entity.HasIndex(e => new { e.OwnerId, e.NormalizedName }).IsUnique();
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure AssetUserTag entity
        modelBuilder.Entity<AssetUserTag>(entity =>
        {
            entity.HasKey(e => new { e.AssetId, e.UserTagId });

            entity.HasOne(e => e.Asset)
                .WithMany(a => a.UserTags)
                .HasForeignKey(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.UserTag)
                .WithMany(t => t.AssetLinks)
                .HasForeignKey(e => e.UserTagId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.AssetId);
            entity.HasIndex(e => e.UserTagId);
        });

        // Configure AssetMlJob entity
        modelBuilder.Entity<AssetMlJob>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.ErrorMessage).HasMaxLength(2000);

            entity.HasOne(e => e.Asset)
                .WithMany(a => a.MlJobs)
                .HasForeignKey(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.AssetId);
            entity.HasIndex(e => new { e.AssetId, e.JobType, e.Status });
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.StartedAt).HasColumnType("timestamp without time zone").HasConversion(NullableUtcConverter);
            entity.Property(e => e.CompletedAt).HasColumnType("timestamp without time zone").HasConversion(NullableUtcConverter);
        });

        // Configure Setting entity
        modelBuilder.Entity<Setting>(entity =>
        {
            entity.HasKey(e => new { e.OwnerId, e.Key });
            entity.Property(e => e.Key).HasMaxLength(100);
            entity.Property(e => e.Value);  // text, no length limit
            entity.Property(e => e.UpdatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure Album entity
        modelBuilder.Entity<Album>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Name).IsRequired().HasMaxLength(200);
            entity.Property(e => e.Description).HasMaxLength(1000);

            entity.HasOne(e => e.CoverAsset)
                .WithMany()
                .HasForeignKey(e => e.CoverAssetId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasOne(e => e.Owner)
                .WithMany(u => u.OwnedAlbums)
                .HasForeignKey(e => e.OwnerId)
                .OnDelete(DeleteBehavior.Restrict);

            entity.HasIndex(e => e.CoverAssetId);
            entity.HasIndex(e => e.OwnerId);
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.UpdatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure AlbumAsset entity — composite PK (AlbumId, AssetId)
        modelBuilder.Entity<AlbumAsset>(entity =>
        {
            entity.HasKey(e => new { e.AlbumId, e.AssetId });

            entity.HasOne(e => e.Album)
                .WithMany(a => a.AlbumAssets)
                .HasForeignKey(e => e.AlbumId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.Asset)
                .WithMany()
                .HasForeignKey(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.AlbumId);
            entity.HasIndex(e => e.AssetId);
            entity.Property(e => e.AddedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure AlbumPermission entity
        modelBuilder.Entity<AlbumPermission>(entity =>
        {
            entity.HasKey(e => e.Id);

            entity.HasOne(e => e.Album)
                .WithMany(a => a.Permissions)
                .HasForeignKey(e => e.AlbumId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.User)
                .WithMany(u => u.AlbumPermissions)
                .HasForeignKey(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.GrantedByUser)
                .WithMany()
                .HasForeignKey(e => e.GrantedByUserId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasIndex(e => e.AlbumId);
            entity.HasIndex(e => e.UserId);
            entity.HasIndex(e => new { e.AlbumId, e.UserId }).IsUnique();
            entity.Property(e => e.GrantedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure SharedLink entity
        modelBuilder.Entity<SharedLink>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Token).IsRequired().HasMaxLength(64);
            entity.HasIndex(e => e.Token).IsUnique();

            entity.HasOne(e => e.Asset)
                .WithMany()
                .HasForeignKey(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.Album)
                .WithMany()
                .HasForeignKey(e => e.AlbumId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.CreatedBy)
                .WithMany()
                .HasForeignKey(e => e.CreatedById)
                .OnDelete(DeleteBehavior.Cascade);

            entity.Property(e => e.PasswordHash).HasMaxLength(200);
            entity.Property(e => e.AllowDownload).HasDefaultValue(true);
            entity.Property(e => e.ViewCount).HasDefaultValue(0);
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.ExpiresAt).HasColumnType("timestamp without time zone").HasConversion(NullableUtcConverter);
        });

        // Configure Notification entity
        modelBuilder.Entity<Notification>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Title).IsRequired().HasMaxLength(200);
            entity.Property(e => e.Message).IsRequired().HasMaxLength(1000);
            entity.Property(e => e.ActionUrl).HasMaxLength(500);
            entity.Property(e => e.IsRead).HasDefaultValue(false);

            entity.HasOne(e => e.User)
                .WithMany()
                .HasForeignKey(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.UserId);
            entity.HasIndex(e => new { e.UserId, e.IsRead });
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure ExternalLibrary entity
        modelBuilder.Entity<ExternalLibrary>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Name).IsRequired().HasMaxLength(200);
            entity.Property(e => e.Path).IsRequired().HasMaxLength(1000);
            entity.Property(e => e.CronSchedule).HasMaxLength(100);

            entity.HasOne(e => e.Owner)
                .WithMany(u => u.ExternalLibraries)
                .HasForeignKey(e => e.OwnerId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.OwnerId);
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.LastScannedAt).HasColumnType("timestamp without time zone").HasConversion(NullableUtcConverter);
        });

        // Configure ExternalLibraryPermission entity
        modelBuilder.Entity<ExternalLibraryPermission>(entity =>
        {
            entity.HasKey(e => e.Id);

            entity.HasOne(e => e.ExternalLibrary)
                .WithMany(l => l.Permissions)
                .HasForeignKey(e => e.ExternalLibraryId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.User)
                .WithMany(u => u.ExternalLibraryPermissions)
                .HasForeignKey(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.GrantedByUser)
                .WithMany()
                .HasForeignKey(e => e.GrantedByUserId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasIndex(e => new { e.ExternalLibraryId, e.UserId }).IsUnique();
            entity.Property(e => e.GrantedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure Person entity (face cluster)
        modelBuilder.Entity<Person>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Name).HasMaxLength(200);

            entity.HasOne(e => e.Owner)
                .WithMany()
                .HasForeignKey(e => e.OwnerId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.CoverFace)
                .WithMany()
                .HasForeignKey(e => e.CoverFaceId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasIndex(e => e.OwnerId);
            entity.HasIndex(e => new { e.OwnerId, e.IsHidden });
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.UpdatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        // Configure Face entity (detected face with embedding)
        modelBuilder.Entity<Face>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Embedding).HasColumnType("vector(512)");

            entity.HasOne(e => e.Asset)
                .WithMany(a => a.Faces)
                .HasForeignKey(e => e.AssetId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(e => e.Person)
                .WithMany(p => p.Faces)
                .HasForeignKey(e => e.PersonId)
                .OnDelete(DeleteBehavior.SetNull);

            entity.HasIndex(e => e.AssetId);
            entity.HasIndex(e => e.PersonId);
            entity.HasIndex(e => new { e.PersonId, e.IsRejected });
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
        });

        modelBuilder.Entity<Asset>(entity =>
        {
            entity.Property(e => e.FaceDetectionCompletedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(NullableUtcConverter);
        });

        // Configure RefreshToken entity
        modelBuilder.Entity<RefreshToken>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.TokenHash).IsRequired().HasMaxLength(256);
            entity.Property(e => e.DeviceId).IsRequired().HasMaxLength(200);

            entity.HasOne(e => e.User)
                .WithMany(u => u.RefreshTokens)
                .HasForeignKey(e => e.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => e.UserId);
            entity.HasIndex(e => new { e.UserId, e.DeviceId });
            entity.HasIndex(e => e.TokenHash).IsUnique();
            entity.Property(e => e.CreatedAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.ExpiresAt).HasColumnType("timestamp without time zone").HasConversion(UtcConverter);
            entity.Property(e => e.RevokedAt).HasColumnType("timestamp without time zone").HasConversion(NullableUtcConverter);
        });
    }
}
