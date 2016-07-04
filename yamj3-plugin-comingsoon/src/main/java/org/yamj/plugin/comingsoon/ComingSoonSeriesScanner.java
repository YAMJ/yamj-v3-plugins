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
package org.yamj.plugin.comingsoon;

import static org.yamj.plugin.api.Constants.UTF8;

import java.io.IOException;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.DigestedResponse;
import org.yamj.api.common.tools.ResponseTools;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.model.IEpisode;
import org.yamj.plugin.api.model.ISeason;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.model.type.JobType;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.TemporaryUnavailableException;
import ro.fortsoft.pf4j.Extension;
 
@Extension
public final class ComingSoonSeriesScanner extends AbstractComingSoonScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ComingSoonSeriesScanner.class);
    private static final String COMINGSOON_SERIES_URL = "serietv/scheda/?";

    @Override
    public boolean isValidSeriesId(String seriesId) {
        return isValidComingSoonId(seriesId);
    }
    
    @Override
    public boolean scanSeries(ISeries series, boolean throwTempError) {
        final String comingSoonId = series.getId(SCANNER_NAME);
        if (isNoValidComingSoonId(comingSoonId)) {
            LOG.debug("ComingSoon id not available '{}'", series.getTitle());
            return false;
        }

        try {
            final String url = COMINGSOON_BASE_URL + COMINGSOON_SERIES_URL + COMINGSOON_KEY_PARAM + comingSoonId;
            DigestedResponse response = httpClient.requestContent(url, UTF8);
            if (throwTempError && ResponseTools.isTemporaryError(response)) {
                throw new TemporaryUnavailableException("ComingSoon service is temporary not available: " + response.getStatusCode());
            } else if (ResponseTools.isNotOK(response)) {
                throw new RuntimeException("ComingSoon request failed: " + response.getStatusCode());
            }
            String xml = response.getContent();
            
            // TITLE
            int beginIndex = xml.indexOf("<h1 class=\"titolo");
            if (beginIndex < 0 ) {
                LOG.error("No title found at ComingSoon page. HTML layout has changed?");
                return false;
            }

            String tag = xml.substring(beginIndex, xml.indexOf(">", beginIndex)+1);
            String title = HTMLTools.extractTag(xml, tag, "</h1>").trim();
            if (StringUtils.isBlank(title)) {
                return false;
            }
            title = WordUtils.capitalizeFully(title);
    
            final String originalTitle = parseTitleOriginal(xml);
            final String plot = parsePlot(xml);
            
            series.setTitle(title);
            series.setOriginalTitle(originalTitle);
            series.setPlot(plot);
            series.setOutline(plot);
            series.setRating(parseRating(xml));
            series.setCountries(parseCountries(xml));
            series.setStudios(parseStudios(xml));
            series.setGenres(parseGenres(xml));
            
            String year = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">ANNO</span>:", "</li>")).trim();
            int intYear = NumberUtils.toInt(year, 0); 
            if (intYear > 1900) {
                series.setStartYear(intYear);
            }
    
            // ACTORS
            List<ComingSoonActor> actors;
            if (configService.isCastScanEnabled(JobType.ACTOR)) {
                actors = parseActors(xml);
            } else {
                actors = Collections.emptyList();
            }
            
            // scan seasons and episodes
            scanSeasons(series, comingSoonId, title, originalTitle, plot, actors);
        
            return true;
        } catch (IOException ioe) {
            throw new RuntimeException("ComingSoon scanning error", ioe);
        }
    }

    private void scanSeasons(ISeries series, String comingSoonId, String title, String originalTitle, String plot, Collection<ComingSoonActor> actors) {
        
        for (ISeason season : series.getSeasons()) {
            String seasonXML = getSeasonXml(comingSoonId, season.getNumber());

            // nothing to do if season already done
            if (!season.isDone()) {
                // use values from series
                season.addId(SCANNER_NAME, comingSoonId);
                season.setTitle(title);
                season.setOriginalTitle(originalTitle);
                season.setPlot(plot);
                season.setOriginalTitle(plot);

                // TODO start year from season XML for Italy
                
                // mark season as done
                season.setDone();
            }
            
            // scan episodes
            scanEpisodes(season, comingSoonId, seasonXML, actors);
        }
    }

    private void scanEpisodes(ISeason season, String comingSoonId, String seasonXML, Collection<ComingSoonActor> actors) {
        
        // parse episodes from season XML
        Map<Integer,ComingSoonEpisode> comingSoonEpisodes = this.parseEpisodes(seasonXML);

        for (IEpisode episode : season.getEpisodes()) {
            if (episode.isDone()) {
                // nothing to do anymore
                continue;
            }
            
            // get the episode
            ComingSoonEpisode comingSoonEpisode = comingSoonEpisodes.get(episode.getNumber());
            if (comingSoonEpisode == null) {
                // mark episode as not found
                episode.setNotFound();
                continue;
            }
            
            // set coming soon id for episode
            episode.addId(SCANNER_NAME, comingSoonId);
            episode.setTitle(comingSoonEpisode.getTitle());
            episode.setOriginalTitle(comingSoonEpisode.getOriginalTitle());

            for (String director : comingSoonEpisode.getDirectors()) {
                episode.addCredit(JobType.DIRECTOR, director);
            }
            for (String writer : comingSoonEpisode.getWriters()) {
                episode.addCredit(JobType.WRITER, writer);
            }
            for (ComingSoonActor actor : actors) {
                episode.addCredit(actor.getSourceId(), actor.getJobType(), actor.getName(), actor.getRole(), actor.getPhotoUrl());
            }
        }
    }
    
    private String getSeasonXml(String comingSoonId, int season) {
        final String url = COMINGSOON_BASE_URL + "/serietv/scheda/" + comingSoonId + "/episodi/stagione-" + season + "/";

        String xml = null; 
        try {
            DigestedResponse response = httpClient.requestContent(url, UTF8);
            if (ResponseTools.isNotOK(response)) {
                LOG.error("ComingSoon request failed for episodes of season {}-{}: {}", comingSoonId, season, response.getStatusCode());
            } else {
                xml = response.getContent();
            }
        } catch (Exception ex) {
            LOG.error("ComingSoon episodes request failed", ex);
        }
        return xml;
    }
    
    private Map<Integer,ComingSoonEpisode> parseEpisodes(String seasonXML) {
        Map<Integer,ComingSoonEpisode> episodes = new HashMap<>();
        if (StringUtils.isBlank(seasonXML)) return episodes;
        
        List<String> tags = HTMLTools.extractTags(seasonXML, "BOX LISTA EPISODI SERIE TV", "BOX LISTA EPISODI SERIE TV", "<div class=\"box-contenitore", "<!-");
        for (String tag : tags) {
            int number = NumberUtils.toInt(HTMLTools.extractTag(tag, "episode=\"", "\""), -1);
            if (number > -1) {
                ComingSoonEpisode episode = new ComingSoonEpisode(number);
                episode.setTitle(HTMLTools.extractTag(tag, "img title=\"", "\""));
                episode.setOriginalTitle(HTMLTools.extractTag(tag, " descrizione\">", "</div>"));
                    
                if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
                    episode.setDirectors(parseEpisodeCredits(tag, ">REGIA</strong>:"));
                }

                if (configService.isCastScanEnabled(JobType.WRITER)) {
                    episode.setWriters(parseEpisodeCredits(tag, ">SCENEGGIATURA</strong>:"));
                }

                episodes.put(episode.getNumber(), episode);
            }
        }
        
        return episodes;
    }
    
    private static List<String> parseEpisodeCredits(String xml, String startTag) {
        List<String> names = new ArrayList<>();
        for (String name : HTMLTools.extractTag(xml, startTag, "</li>").split(",")) {
            if (StringUtils.isNotBlank(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static List<ComingSoonActor> parseActors(String xml) {
        List<ComingSoonActor> actors = new ArrayList<>();
        for (String tag : HTMLTools.extractTags(xml, "Il Cast</div>", "IL CAST -->", "<a href=\"/personaggi/", "</a>", false)) {
            String name = HTMLTools.extractTag(tag, "<div class=\"h6 titolo\">", "</div>");
            String role = HTMLTools.extractTag(tag, "<div class=\"h6 descrizione\">", "</div>");
            
            String sourceId = null;
            int beginIndex = tag.indexOf('/');
            if (beginIndex >-1) {
                int endIndex = tag.indexOf('/', beginIndex+1);
                if (endIndex > beginIndex) {
                    sourceId = tag.substring(beginIndex+1, endIndex);
                }
            }
            
            ComingSoonActor actor = new ComingSoonActor(sourceId, JobType.ACTOR, name, role);
            final String posterURL = HTMLTools.extractTag(tag, "<img src=\"", "\"");
            if (posterURL.contains("http")) {
                actor.setPhotoUrl(posterURL.replace("_ico.jpg", ".jpg"));
            }
            actors.add(actor);
        }
        return actors;
    }
    
    /**
     * Inner class for actors.
     */
    public static class ComingSoonActor {
        
        private final String sourceId;
        private final JobType jobType;
        private final String name;
        private final String role;
        private String photoUrl;

        public ComingSoonActor(String sourceId, JobType jobType, String name) {
            this(sourceId, jobType, name, null);
        }

        public ComingSoonActor(String sourceId, JobType jobType, String name, String role) {
            this.sourceId = sourceId;
            this.jobType = jobType;
            this.name = name;
            this.role = role;
        }

        public String getSourceId() {
            return sourceId;
        }

        public JobType getJobType() {
            return jobType;
        }

        public String getName() {
            return name;
        }

        public String getRole() {
            return role;
        }
        
        public String getPhotoUrl() {
            return photoUrl;
        }

        public void setPhotoUrl(String photoUrl) {
            this.photoUrl = photoUrl;
        }
    }
    
    /**
     * Inner class for episodes.
     */
    public static class ComingSoonEpisode {
        
        private final int number;
        private String title;
        private String originalTitle;
        private List<String> directors = Collections.emptyList();
        private List<String> writers = Collections.emptyList();

        public ComingSoonEpisode(int number) {
            this.number = number;
        }
        
        public int getNumber() {
            return number;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getOriginalTitle() {
            return originalTitle;
        }

        public void setOriginalTitle(String originalTitle) {
            this.originalTitle = originalTitle;
        }

        public List<String> getDirectors() {
            return directors;
        }

        public void setDirectors(List<String> directors) {
            this.directors = directors;
        }

        public List<String> getWriters() {
            return writers;
        }

        public void setWriters(List<String> writers) {
            this.writers = writers;
        }
    }
}
