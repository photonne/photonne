using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Models;

namespace PhotoHub.Server.Api.Shared.Data;

public class ApplicationDbContext : DbContext
{
    public ApplicationDbContext(DbContextOptions<ApplicationDbContext> options) : base(options)
    {
    }

    // PhotoEntity removed - use Asset instead
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

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        // PhotoEntity configuration removed - use Asset instead

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
            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
            entity.Property(e => e.LastLoginAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.HasValue && v.Value.Kind == DateTimeKind.Utc 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Unspecified) 
                        : v,
                    v => v.HasValue 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Utc) 
                        : null);
        });

        // Configure Folder entity
        modelBuilder.Entity<Folder>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Path).IsRequired().HasMaxLength(1000);
            entity.Property(e => e.Name).IsRequired().HasMaxLength(500);
            entity.HasIndex(e => e.Path).IsUnique();
            entity.HasIndex(e => e.ParentFolderId);
            
            // Self-referencing relationship for folder hierarchy
            entity.HasOne(e => e.ParentFolder)
                .WithMany(e => e.SubFolders)
                .HasForeignKey(e => e.ParentFolderId)
                .OnDelete(DeleteBehavior.Restrict);
            
            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
        });

        // Configure FolderPermission entity
        modelBuilder.Entity<FolderPermission>(entity =>
        {
            entity.HasKey(e => e.Id);
            
            // Foreign keys
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
            
            // Unique constraint: one permission record per user-folder combination
            entity.HasIndex(e => new { e.UserId, e.FolderId }).IsUnique();
            
            entity.Property(e => e.GrantedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
        });
        
        // Configure Asset entity
        modelBuilder.Entity<Asset>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.FileName).IsRequired().HasMaxLength(500);
            entity.Property(e => e.FullPath).IsRequired().HasMaxLength(1000);
            entity.Property(e => e.Checksum).IsRequired().HasMaxLength(64);
            entity.Property(e => e.Extension).IsRequired().HasMaxLength(10);
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
            
            entity.Property(e => e.CreatedDate)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
            
            entity.Property(e => e.ModifiedDate)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
            
            entity.Property(e => e.ScannedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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
            
            entity.Property(e => e.DateTimeOriginal)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.HasValue && v.Value.Kind == DateTimeKind.Utc 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Unspecified) 
                        : v,
                    v => v.HasValue 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Utc) 
                        : null);
            
            entity.Property(e => e.ExtractedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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
            entity.HasIndex(e => new { e.AssetId, e.Size }).IsUnique(); // One thumbnail per size per asset
            
            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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
            entity.HasIndex(e => new { e.AssetId, e.TagType }).IsUnique(); // One tag per type per asset
            
            entity.Property(e => e.DetectedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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

            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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
            entity.HasIndex(e => new { e.AssetId, e.JobType, e.Status }); // For efficient querying
            
            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
            
            entity.Property(e => e.StartedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.HasValue && v.Value.Kind == DateTimeKind.Utc 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Unspecified) 
                        : v,
                    v => v.HasValue 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Utc) 
                        : null);
            
            entity.Property(e => e.CompletedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.HasValue && v.Value.Kind == DateTimeKind.Utc 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Unspecified) 
                        : v,
                    v => v.HasValue 
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Utc) 
                        : null);
        });

        // Configure Setting entity
        modelBuilder.Entity<Setting>(entity =>
        {
            entity.HasKey(e => new { e.UserId, e.Key });
            entity.Property(e => e.UserId);
            entity.Property(e => e.Key).HasMaxLength(100);
            entity.Property(e => e.Value).HasMaxLength(1000);
            entity.Property(e => e.UpdatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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
            
            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
            
            entity.Property(e => e.UpdatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
        });

        // Configure AlbumAsset entity
        modelBuilder.Entity<AlbumAsset>(entity =>
        {
            entity.HasKey(e => e.Id);
            
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
            entity.HasIndex(e => new { e.AlbumId, e.AssetId }).IsUnique(); // One asset can only appear once per album
            
            entity.Property(e => e.AddedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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
            entity.HasIndex(e => new { e.AlbumId, e.UserId }).IsUnique(); // One permission per user-album combination
            
            entity.Property(e => e.GrantedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
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

            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));

            entity.Property(e => e.ExpiresAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.HasValue && v.Value.Kind == DateTimeKind.Utc
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Unspecified) : v,
                    v => v.HasValue ? DateTime.SpecifyKind(v.Value, DateTimeKind.Utc) : null);

            entity.Property(e => e.PasswordHash).HasMaxLength(200);
            entity.Property(e => e.AllowDownload).HasDefaultValue(true);
            entity.Property(e => e.MaxViews);
            entity.Property(e => e.ViewCount).HasDefaultValue(0);

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

            entity.Property(e => e.CreatedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));

            entity.Property(e => e.ExpiresAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.Kind == DateTimeKind.Utc ? DateTime.SpecifyKind(v, DateTimeKind.Unspecified) : v,
                    v => DateTime.SpecifyKind(v, DateTimeKind.Utc));

            entity.Property(e => e.RevokedAt)
                .HasColumnType("timestamp without time zone")
                .HasConversion(
                    v => v.HasValue && v.Value.Kind == DateTimeKind.Utc
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Unspecified)
                        : v,
                    v => v.HasValue
                        ? DateTime.SpecifyKind(v.Value, DateTimeKind.Utc)
                        : null);
        });
    }
}

