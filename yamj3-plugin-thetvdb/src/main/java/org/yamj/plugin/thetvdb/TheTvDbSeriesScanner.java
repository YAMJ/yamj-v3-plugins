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
package org.yamj.plugin.thetvdb;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.Constants.SOURCE_TVDB;

import com.omertron.thetvdbapi.model.Actor;
import com.omertron.thetvdbapi.model.Episode;
import com.omertron.thetvdbapi.model.Series;
import java.util.*;
import org.apache.commons.collections.CollectionUtils;
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
public final class TheTvDbSeriesScanner extends AbstractTheTvDbScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TheTvDbSeriesScanner.class);

    @Override
    public boolean isValidSeriesId(String movieId) {
        return isValidTheTvDbId(movieId);
    }

    @Override
    public boolean scanSeries(ISeries series, boolean throwTempError) {
        // get series id
        final String tvdbId = series.getId(SOURCE_TVDB);
        if (isNoValidTheTvDbId(tvdbId)) {
            LOG.debug("TVDb id not available '{}'", series.getTitle());
            return false;
        }
        
        // get series info
        Locale locale = localeService.getLocale();
        Series tvdbSeries = theTvDbApiWrapper.getSeries(tvdbId, locale.getLanguage(), throwTempError);
        if (tvdbSeries == null || StringUtils.isBlank(tvdbSeries.getId())) {
            LOG.error("Can't find informations for series '{}'", series.getTitle());
            return false;
        }
        
        // fill in data
        series.addId(SOURCE_IMDB, tvdbSeries.getImdbId());
        series.setTitle(tvdbSeries.getSeriesName());  
        series.setPlot(tvdbSeries.getOverview());
        series.setOutline(tvdbSeries.getOverview());
        series.setRating(MetadataTools.parseRating(tvdbSeries.getRating()));
        series.setGenres(tvdbSeries.getGenres());

        String faDate = tvdbSeries.getFirstAired();
        if (StringUtils.isNotBlank(faDate) && (faDate.length() >= 4)) {
            try {
                series.setStartYear(Integer.parseInt(faDate.substring(0, 4)));
            } catch (Exception ignore) { //NOSONAR
                // ignore error if year is invalid
            }
        }

        if (StringUtils.isNotBlank(tvdbSeries.getNetwork())) {
            Set<String> studios = Collections.singleton(tvdbSeries.getNetwork().trim());
            series.setStudios(studios);
        }

        // ACTORS (to store in episodes)
        final List<Actor> actors;
        if (configService.isCastScanEnabled(JobType.ACTOR)) {
            actors = theTvDbApiWrapper.getActors(tvdbSeries.getId());
        } else {
            actors = null;
        }
        
        // SCAN SEASONS
        scanSeasons(series, tvdbSeries, actors, locale);

        return true;
    }

    private void scanSeasons(ISeries series, Series tvdbSeries, List<Actor> actors, Locale locale) {
        for (ISeason season : series.getSeasons()) {

            // nothing to do if season already done
            if (!season.isDone()) {
                // same as series id
                final String tvdbId = tvdbSeries.getId();
                season.addId(SOURCE_TVDB, tvdbId);
                season.setTitle(tvdbSeries.getSeriesName());
                season.setPlot(tvdbSeries.getOverview());
                season.setOutline(tvdbSeries.getOverview());

                // get season year from minimal first aired of episodes
                String year = theTvDbApiWrapper.getSeasonYear(tvdbId, season.getNumber(), locale.getLanguage());
                season.setYear(MetadataTools.extractYearAsInt(year));
    
                // mark season as done
                season.setDone();
            }
            
            // scan episodes
            scanEpisodes(season, actors, locale);
        }
    }

    private void scanEpisodes(ISeason season, List<Actor> actors, Locale locale) {
        final String seriesId = season.getSeries().getId(SOURCE_TVDB);

        for (IEpisode episode : season.getEpisodes()) {
            if (episode.isDone()) {
                // nothing to do anymore
                continue;
            }
            
            Episode tvdbEpisode = theTvDbApiWrapper.getEpisode(seriesId, season.getNumber(), episode.getNumber(), locale.getLanguage());
            if (tvdbEpisode == null || StringUtils.isBlank(tvdbEpisode.getId())) {
                // mark episode as not found
                episode.setNotFound();
                continue;
            }

            // set season id
            season.addId(SOURCE_TVDB, tvdbEpisode.getSeasonId());
            
            // fill in data
            episode.addId(SOURCE_TVDB, tvdbEpisode.getId());
            episode.addId(SOURCE_IMDB, tvdbEpisode.getImdbId());
            episode.setTitle(tvdbEpisode.getEpisodeName());
            episode.setPlot(tvdbEpisode.getOverview());
            episode.setOutline(tvdbEpisode.getOverview());
            episode.setRelease(MetadataTools.parseToDate(tvdbEpisode.getFirstAired()));

            // directors
            addCredits(episode, JobType.DIRECTOR, tvdbEpisode.getDirectors());
            // writers
            addCredits(episode, JobType.WRITER, tvdbEpisode.getWriters());
            // actors
            addActors(episode, actors);
            // guest stars
            addCredits(episode, JobType.GUEST_STAR, tvdbEpisode.getGuestStars());

            // mark episode as done
            episode.setDone();
        }
    }

    private static void addActors(IEpisode episode, Collection<Actor> actors) {
        if (CollectionUtils.isNotEmpty(actors)) {
            for (Actor actor : actors) {
                final String sourceId = (actor.getId() > 0 ? Integer.toString(actor.getId()) : null);
                episode.addCredit(sourceId, JobType.ACTOR, actor.getName(), actor.getRole());
            }
        }
    }

    private void addCredits(IEpisode episode, JobType jobType, Collection<String> persons) {
        if (persons != null && configService.isCastScanEnabled(jobType)) {
            for (String person : persons) {
                episode.addCredit(jobType, person);
            }
        }
    }
}
