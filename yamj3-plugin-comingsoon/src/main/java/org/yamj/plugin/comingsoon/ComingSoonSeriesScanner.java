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

import static org.yamj.plugin.api.common.Constants.UTF8;

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
import org.yamj.plugin.api.metadata.dto.*;
import org.yamj.plugin.api.type.JobType;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.TemporaryUnavailableException;
import ro.fortsoft.pf4j.Extension;
 
@Extension
public class ComingSoonSeriesScanner extends AbstractComingSoonScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ComingSoonSeriesScanner.class);
    private static final String COMINGSOON_SERIES_URL = "serietv/scheda/?";

    @Override
    public String getSeriesId(String title, String originalTitle, int year, Map<String, String> ids, boolean throwTempError) {
        String comingSoonId = ids.get(SCANNER_NAME);
        if (StringUtils.isNotBlank(comingSoonId)) {
            return comingSoonId;
        }
        
        // search coming soon site by title
        comingSoonId = getComingSoonId(title, year, true, throwTempError);

        // search coming soon site by original title
        if (isNoValidComingSoonId(comingSoonId) && StringUtils.isNotBlank(originalTitle) && !StringUtils.equalsIgnoreCase(title, originalTitle)) {
            comingSoonId = getComingSoonId(originalTitle, year, true, throwTempError);
        }

        // search coming soon with search engine tools
        if (isNoValidComingSoonId(comingSoonId)) {
            comingSoonId = this.searchEngineTools.searchURL(title, year, "www.comingsoon.it/serietv", throwTempError);
            int beginIndex = comingSoonId.indexOf("serietv/");
            if (beginIndex < 0) {
                comingSoonId = null;
            } else {
                beginIndex = comingSoonId.indexOf("/", beginIndex+9);
                int endIndex = comingSoonId.indexOf("/", beginIndex+1);
                if (beginIndex < endIndex) {
                    comingSoonId = comingSoonId.substring(beginIndex+1, endIndex);
                } else {
                    comingSoonId = null;
                }
            }
        }
        
        if (isNoValidComingSoonId(comingSoonId)) {
            return null;
        }
        
        return comingSoonId;
    }

    @Override
    public boolean scanSeries(SeriesDTO series, boolean throwTempError) {
        final String comingSoonId = series.getIds().get(SCANNER_NAME);
        if (isNoValidComingSoonId(comingSoonId)) {
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
            if (StringUtils.isBlank(title)) return false;
    
            final String plot = parsePlot(xml);

            series.setTitle(WordUtils.capitalizeFully(title))
                .setOriginalTitle(parseTitleOriginal(xml))
                .setPlot(plot)
                .setOutline(plot)
                .setRating(parseRating(xml))
                .setCountries(parseCountries(xml))
                .setStudios(parseStudios(xml))
                .setGenres(parseGenres(xml));
            
            String year = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">ANNO</span>:", "</li>")).trim();
            int intYear = NumberUtils.toInt(year, 0); 
            if (intYear > 1900) {
                series.setStartYear(intYear);
            }
    
            // ACTORS
            List<CreditDTO> actors;
            if (configService.isCastScanEnabled(JobType.ACTOR)) {
                actors = parseActors(xml);
            } else {
                actors = Collections.emptyList();
            }
            
            // scan seasons and episodes
            scanSeasons(series, comingSoonId, actors);
        
            return true;
        } catch (IOException ioe) {
            throw new RuntimeException("ComingSoon scanning error", ioe);
        }
    }

    private void scanSeasons(SeriesDTO series, String comingSoonId, Collection<CreditDTO> actors) {
        
        for (SeasonDTO season : series.getSeasons()) {
            
            String seasonXML = getSeasonXml(comingSoonId, season.getSeasonNumber());

            if (season.isScanNeeded()) {
                // use values from series
                season.addId(SCANNER_NAME, comingSoonId)
                    .setTitle(series.getTitle())
                    .setOriginalTitle(series.getOriginalTitle())
                    .setPlot(series.getPlot())
                    .setOutline(series.getOutline());

                // TODO start year from season XML for Italy
            }
            
            // scan episodes
            scanEpisodes(season, comingSoonId, seasonXML, actors);
        }
    }

    private void scanEpisodes(SeasonDTO season, String comingSoonId, String seasonXML, Collection<CreditDTO> actors) {
        
        // parse episodes from season XML
        Map<Integer,EpisodeDTO> dtos = this.parseEpisodes(seasonXML);

        for (EpisodeDTO episode : season.getEpisodes()) {
            EpisodeDTO dto = dtos.get(episode.getEpisodeNumber());
            if (dto == null) {
                episode.setValid(false);
                continue;
            }
            
            // set coming soon id for episode
            episode.addId(SCANNER_NAME, comingSoonId)
                   .setTitle(dto.getTitle())
                   .setOriginalTitle(dto.getOriginalTitle())
                   .addCredits(actors)
                   .addCredits(dto.getCredits());
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
    
    private Map<Integer,EpisodeDTO> parseEpisodes(String seasonXML) {
        Map<Integer,EpisodeDTO> episodes = new HashMap<>();
        if (StringUtils.isBlank(seasonXML)) return episodes;
        
        List<String> tags = HTMLTools.extractTags(seasonXML, "BOX LISTA EPISODI SERIE TV", "BOX LISTA EPISODI SERIE TV", "<div class=\"box-contenitore", "<!-");
        for (String tag : tags) {
            int episode = NumberUtils.toInt(HTMLTools.extractTag(tag, "episode=\"", "\""), -1);
            if (episode > -1) {
                EpisodeDTO dto = new EpisodeDTO(null, episode)
                    .setTitle(HTMLTools.extractTag(tag, "img title=\"", "\""))
                    .setOriginalTitle(HTMLTools.extractTag(tag, " descrizione\">", "</div>"));
                    
                if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
                    parseEpisodeCredits(dto, tag, ">REGIA</strong>:", JobType.DIRECTOR);
                }

                if (configService.isCastScanEnabled(JobType.WRITER)) {
                    parseEpisodeCredits(dto, tag, ">SCENEGGIATURA</strong>:", JobType.WRITER);
                }

                episodes.put(dto.getEpisodeNumber(), dto);
            }
        }
        
        return episodes;
    }
    
    private static void parseEpisodeCredits(EpisodeDTO episode, String xml, String startTag, JobType jobType) {
        for (String name : HTMLTools.extractTag(xml, startTag, "</li>").split(",")) {
            if (StringUtils.isNotBlank(name)) {
                episode.addCredit(new CreditDTO(jobType, name));
            }
        }
    }
}
