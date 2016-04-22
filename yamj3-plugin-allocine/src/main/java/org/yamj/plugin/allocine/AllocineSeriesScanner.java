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
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocineSeriesScanner extends AbstractAllocineScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocineSeriesScanner.class);

    @Override
    public boolean isValidSeriesId(String seriesId) {
        return isValidAllocineId(seriesId);
    }

    @Override
    public boolean scanSeries(SeriesDTO seriesDTO, boolean throwTempError) {
        // get series id
        final String allocineId = seriesDTO.getIds().get(SCANNER_NAME);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", seriesDTO.getTitle());
            return false;
        }
        
        // get series info
        TvSeriesInfos tvSeriesInfos = allocineApiWrapper.getTvSeriesInfos(allocineId, throwTempError);
        if (tvSeriesInfos == null || tvSeriesInfos.isNotValid()) {
            LOG.error("Can't find informations for series '{}'", seriesDTO.getTitle());
            return false;
        }

        seriesDTO.setTitle(tvSeriesInfos.getTitle())
            .setOriginalTitle(tvSeriesInfos.getOriginalTitle())
            .setStartYear(tvSeriesInfos.getYearStart())
            .setEndYear(tvSeriesInfos.getYearEnd())
            .setPlot(tvSeriesInfos.getSynopsis())
            .setOutline(tvSeriesInfos.getSynopsisShort())
            .setGenres(tvSeriesInfos.getGenres())
            .addStudio(tvSeriesInfos.getOriginalChannel())
            .setCountries(tvSeriesInfos.getNationalities())
            .setRating(tvSeriesInfos.getUserRating());

        // add awards
        if (tvSeriesInfos.getFestivalAwards() != null && configService.getBooleanProperty("allocine.tvshow.awards", false)) {
            for (FestivalAward festivalAward : tvSeriesInfos.getFestivalAwards()) {
                seriesDTO.addAward(new AwardDTO(SCANNER_NAME, festivalAward.getFestival(), festivalAward.getName(), festivalAward.getYear()));
            }
        }

        // SCAN SEASONS
        scanSeasons(seriesDTO, tvSeriesInfos);

        return false;
    }

    private void scanSeasons(SeriesDTO seriesDTO, TvSeriesInfos tvSeriesInfos) {

        for (SeasonDTO seasonDTO : seriesDTO.getSeasons()) {

            TvSeasonInfos tvSeasonInfos = null;
            if (seasonDTO.getSeasonNumber() <= tvSeriesInfos.getSeasonCount()) {
                int seasonCode = tvSeriesInfos.getSeasonCode(seasonDTO.getSeasonNumber());
                if (seasonCode > 0) {
                    tvSeasonInfos = allocineApiWrapper.getTvSeasonInfos(String.valueOf(seasonCode));
                }
            }

            if (tvSeasonInfos == null || tvSeasonInfos.isNotValid()) {
                seasonDTO.setValid(false);
            } else {
                seasonDTO.addId(SCANNER_NAME, String.valueOf(tvSeasonInfos.getCode()))
                    .setTitle(tvSeriesInfos.getTitle())
                    .setOriginalTitle(tvSeriesInfos.getOriginalTitle())
                    .setPlot(tvSeriesInfos.getSynopsis())
                    .setOutline(tvSeriesInfos.getSynopsisShort())
                    .setYear(tvSeasonInfos.getSeason().getYearStart());
            }
            
            // scan episodes
            scanEpisodes(seasonDTO, tvSeasonInfos);
        }
    }

    private void scanEpisodes(SeasonDTO season, TvSeasonInfos tvSeasonInfos) {
        for (EpisodeDTO episodeDTO : season.getEpisodes()) {

            // get the episode
            String allocineId = episodeDTO.getIds().get(SCANNER_NAME);
            if (StringUtils.isBlank(allocineId) && tvSeasonInfos != null && tvSeasonInfos.isValid()) {
                Episode episode = tvSeasonInfos.getEpisode(episodeDTO.getEpisodeNumber());
                if (episode != null && episode.getCode() > 0) {
                    allocineId = String.valueOf(episode.getCode());
                }
            }

            EpisodeInfos episodeInfos = allocineApiWrapper.getEpisodeInfos(allocineId);
            if (episodeInfos == null || episodeInfos.isNotValid()) {
                episodeDTO.setValid(false);
            } else {
                episodeDTO.addId(SCANNER_NAME, String.valueOf(episodeInfos.getCode()))
                    .setTitle(episodeInfos.getTitle())
                    .setOriginalTitle(episodeInfos.getOriginalTitle())
                    .setPlot(episodeInfos.getSynopsis())
                    .setOutline(episodeInfos.getSynopsisShort())
                    .setReleaseDate(MetadataTools.parseToDate(episodeInfos.getOriginalBroadcastDate()));

                //  parse credits
                parseCredits(episodeDTO, episodeInfos.getEpisode().getCastMember());
            }
        }
    }
        
    private void parseCredits(EpisodeDTO episodeDTO, List<CastMember> castMembers) {
        if (castMembers == null) {
            return;
        }
        for (CastMember member: castMembers) {
            if (member.getShortPerson() == null) {
                continue;
            }
            
            if (member.isActor() && configService.isCastScanEnabled(JobType.ACTOR)) {
                final JobType jobType = (member.isLeadActor() ? JobType.ACTOR : JobType.GUEST_STAR);
                episodeDTO.addCredit(createCredit(member, jobType, member.getRole()));
            } else if (member.isDirector() && configService.isCastScanEnabled(JobType.DIRECTOR)) {
                episodeDTO.addCredit(createCredit(member, JobType.DIRECTOR));
            } else if (member.isWriter() && configService.isCastScanEnabled(JobType.WRITER)) {
                episodeDTO.addCredit(createCredit(member, JobType.WRITER));
            } else if (member.isCamera() && configService.isCastScanEnabled(JobType.CAMERA)) {
                episodeDTO.addCredit(createCredit(member, JobType.CAMERA));
            } else if (member.isProducer() && configService.isCastScanEnabled(JobType.PRODUCER)) {
                episodeDTO.addCredit(createCredit(member, JobType.PRODUCER));
            }
        }
    }

    private static CreditDTO createCredit(CastMember member, JobType jobType) {
        return createCredit(member, jobType, null);
    }

    private static CreditDTO createCredit(CastMember member, JobType jobType, String role) {
        final String sourceId = (member.getShortPerson().getCode() > 0 ?  String.valueOf(member.getShortPerson().getCode()) : null);
        return new CreditDTO(SCANNER_NAME, sourceId, jobType, member.getShortPerson().getName(), role);
    }        
}
