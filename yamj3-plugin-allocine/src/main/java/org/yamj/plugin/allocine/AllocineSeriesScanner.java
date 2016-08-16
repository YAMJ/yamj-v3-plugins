/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.allocine;

import static org.yamj.plugin.allocine.AllocinePlugin.SCANNER_NAME;

import com.moviejukebox.allocine.model.*;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.model.IEpisode;
import org.yamj.plugin.api.model.ISeason;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.model.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocineSeriesScanner extends AbstractAllocineScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocineSeriesScanner.class);

    @Override
    public boolean isValidSeriesId(String seriesId) {
        return isValidAllocineId(seriesId);
    }

    @Override
    public boolean scanSeries(ISeries series, boolean throwTempError) {
        // get series id
        final String allocineId = series.getId(SCANNER_NAME);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", series.getTitle());
            return false;
        }
        
        // get series info
        TvSeriesInfos tvSeriesInfos = allocineApiWrapper.getTvSeriesInfos(allocineId, throwTempError);
        if (tvSeriesInfos == null || tvSeriesInfos.isNotValid()) {
            LOG.error("Can't find informations for series '{}'", series.getTitle());
            return false;
        }

        series.setTitle(tvSeriesInfos.getTitle());
        series.setOriginalTitle(tvSeriesInfos.getOriginalTitle());
        series.setStartYear(tvSeriesInfos.getYearStart());
        series.setEndYear(tvSeriesInfos.getYearEnd());
        series.setPlot(tvSeriesInfos.getSynopsis());
        series.setOutline(tvSeriesInfos.getSynopsisShort());
        series.setGenres(tvSeriesInfos.getGenres());
        series.setCountries(tvSeriesInfos.getNationalities());
        series.setRating(tvSeriesInfos.getUserRating());
        
        if (StringUtils.isNotBlank(tvSeriesInfos.getOriginalChannel())) {
            Set<String> studios = Collections.singleton(tvSeriesInfos.getOriginalChannel());
            series.setStudios(studios);
        }

        // add awards
        if (tvSeriesInfos.getFestivalAwards() != null && configService.getBooleanProperty("allocine.tvshow.awards", false)) {
            for (FestivalAward festivalAward : tvSeriesInfos.getFestivalAwards()) {
                series.addAward(festivalAward.getFestival(), festivalAward.getName(), festivalAward.getYear());
            }
        }

        // SCAN SEASONS
        scanSeasons(series, tvSeriesInfos);

        return true;
    }

    private void scanSeasons(ISeries series, TvSeriesInfos tvSeriesInfos) {

        for (ISeason season : series.getSeasons()) {

            TvSeasonInfos tvSeasonInfos = null;
            if (season.getNumber() <= tvSeriesInfos.getSeasonCount()) {
                int seasonCode = tvSeriesInfos.getSeasonCode(season.getNumber());
                if (seasonCode > 0) {
                    tvSeasonInfos = allocineApiWrapper.getTvSeasonInfos(String.valueOf(seasonCode));
                }
            }

            // nothing to do if season already done
            if (!season.isDone()) {
                if (tvSeasonInfos == null || tvSeasonInfos.isNotValid()) {
                    // mark season as not found
                    season.setNotFound();
                } else {
                    season.addId(SCANNER_NAME, String.valueOf(tvSeasonInfos.getCode()));
                    season.setTitle(tvSeriesInfos.getTitle());
                    season.setOriginalTitle(tvSeriesInfos.getOriginalTitle());
                    season.setPlot(tvSeriesInfos.getSynopsis());
                    season.setOutline(tvSeriesInfos.getSynopsisShort());
                    season.setYear(tvSeasonInfos.getSeason().getYearStart());

                    // mark season as done
                    season.setDone();
                }
            }
            
            // scan episodes
            scanEpisodes(season, tvSeasonInfos);
        }
    }

    private void scanEpisodes(ISeason season, TvSeasonInfos tvSeasonInfos) {
        for (IEpisode episode : season.getEpisodes()) {
            if (episode.isDone()) {
                // nothing to do anymore
                continue;
            }
            
            // get the episode
            String allocineId = episode.getId(SCANNER_NAME);
            if (StringUtils.isBlank(allocineId) && tvSeasonInfos != null && tvSeasonInfos.isValid()) {
                com.moviejukebox.allocine.model.Episode allocineEpisode = tvSeasonInfos.getEpisode(episode.getNumber());
                if (allocineEpisode != null && allocineEpisode.getCode() > 0) {
                    allocineId = String.valueOf(allocineEpisode.getCode());
                }
            }

            EpisodeInfos episodeInfos = allocineApiWrapper.getEpisodeInfos(allocineId);
            if (episodeInfos == null || episodeInfos.isNotValid()) {
                // mark episode as not found
                episode.setNotFound();
                continue;
            }

            episode.addId(SCANNER_NAME, String.valueOf(episodeInfos.getCode()));
            episode.setTitle(episodeInfos.getTitle());
            episode.setOriginalTitle(episodeInfos.getOriginalTitle());
            episode.setPlot(episodeInfos.getSynopsis());
            episode.setOutline(episodeInfos.getSynopsisShort());
            episode.setRelease(MetadataTools.parseToDate(episodeInfos.getOriginalBroadcastDate()));

            //  parse credits
            parseCredits(episode, episodeInfos.getEpisode().getCastMember());
            
            // mark episode as done
            episode.setDone();
        }
    }
        
    private void parseCredits(IEpisode episode, List<CastMember> castMembers) {
        if (castMembers == null) {
            return;
        }
        for (CastMember member: castMembers) {
            if (member.getShortPerson() == null) {
                continue;
            }
            
            if (member.isActor()) {
                final JobType jobType = member.isLeadActor() ? JobType.ACTOR : JobType.GUEST_STAR;
                if (configService.isCastScanEnabled(jobType)) {
                    addCredit(episode, member, jobType, member.getRole());
                }
            } else if (member.isDirector() && configService.isCastScanEnabled(JobType.DIRECTOR)) {
                addCredit(episode, member, JobType.DIRECTOR);
            } else if (member.isWriter() && configService.isCastScanEnabled(JobType.WRITER)) {
                addCredit(episode, member, JobType.WRITER);
            } else if (member.isProducer() && configService.isCastScanEnabled(JobType.PRODUCER)) {
                addCredit(episode, member, JobType.PRODUCER);
            } else if (member.isCamera() && configService.isCastScanEnabled(JobType.CAMERA)) {
                addCredit(episode, member, JobType.CAMERA);
            } else if (member.isArt() && configService.isCastScanEnabled(JobType.ART)) {
                addCredit(episode, member, JobType.ART);
            }
        }
    }

    private static void addCredit(IEpisode episode, CastMember member, JobType jobType) {
        addCredit(episode, member, jobType, null);
    }

    private static void addCredit(IEpisode episode, CastMember member, JobType jobType, String role) {
        final String sourceId = member.getShortPerson().getCode() > 0 ?  Integer.toString(member.getShortPerson().getCode()) : null;
        episode.addCredit(sourceId, jobType, member.getShortPerson().getName(), role);
    }        
}
