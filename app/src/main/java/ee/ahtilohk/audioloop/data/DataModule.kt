package ee.ahtilohk.audioloop.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideRecordingDao(database: AppDatabase): RecordingDao {
        return database.recordingDao()
    }

    @Provides
    @Singleton
    fun provideAudioRepository(@ApplicationContext context: Context): AudioRepository {
        return AudioRepository(context)
    }

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager {
        return SettingsManager(context)
    }

    @Provides
    @Singleton
    fun provideAudioProcessingManager(@ApplicationContext context: Context): AudioProcessingManager {
        return AudioProcessingManager(context)
    }

    @Provides
    @Singleton
    fun providePlaylistManager(@ApplicationContext context: Context): PlaylistManager {
        return PlaylistManager(context)
    }

    @Provides
    @Singleton
    fun provideDriveBackupManager(@ApplicationContext context: Context): DriveBackupManager {
        return DriveBackupManager(context)
    }

    @Provides
    @Singleton
    fun providePracticeStatsManager(@ApplicationContext context: Context): PracticeStatsManager {
        return PracticeStatsManager(context)
    }
    
    @Provides
    @Singleton
    fun provideCoachEngine(@ApplicationContext context: Context, statsManager: PracticeStatsManager): ee.ahtilohk.audioloop.CoachEngine {
        return ee.ahtilohk.audioloop.CoachEngine(context, statsManager)
    }
}
