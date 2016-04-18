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

import static org.yamj.plugin.api.tools.Constants.UTF8;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.DigestedResponse;
import org.yamj.api.common.tools.ResponseTools;
import org.yamj.plugin.api.metadata.Credit;
import org.yamj.plugin.api.metadata.Movie;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.tools.MetadataTools;
import org.yamj.plugin.api.type.JobType;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.TemporaryUnavailableException;
import ro.fortsoft.pf4j.Extension;
 
@Extension
public class ComingSoonMovieScanner extends AbstractComingSoonScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ComingSoonMovieScanner.class);
    private static final String COMINGSOON_MOVIE_URL = "film/scheda/?";
    private static final String COMINGSOON_PERSONAGGI = "personaggi/";

    @Override
    public String getMovieId(String title, String originalTitle, int year, Map<String, String> ids, boolean throwTempError) {
        String comingSoonId = ids.get(SCANNER_NAME);
        if (StringUtils.isNotBlank(comingSoonId)) {
            return comingSoonId;
        }
        
        // search coming soon site by title
        comingSoonId = getComingSoonId(title, year, false, throwTempError);

        // search coming soon site by original title
        if (isNoValidComingSoonId(comingSoonId) && StringUtils.isNotBlank(originalTitle) && !StringUtils.equalsIgnoreCase(title, originalTitle)) {
            comingSoonId = getComingSoonId(originalTitle, year, false, throwTempError);
        }

        // search coming soon with search engine tools
        if (isNoValidComingSoonId(comingSoonId)) {
            comingSoonId = this.searchEngineTools.searchURL(title, year, "www.comingsoon.it/film", throwTempError);
            int beginIndex = comingSoonId.indexOf("film/");
            if (beginIndex < 0) {
                comingSoonId = null;
            } else {
                beginIndex = comingSoonId.indexOf("/", beginIndex+6);
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
    public boolean scanMovie(Movie movie, boolean throwTempError) {
        final String comingSoonId = movie.getIds().get(SCANNER_NAME);
        if (isNoValidComingSoonId(comingSoonId)) {
            return false;
        }

        try {
            final String url = COMINGSOON_BASE_URL + COMINGSOON_MOVIE_URL + COMINGSOON_KEY_PARAM + comingSoonId;
            DigestedResponse response = httpClient.requestContent(url, UTF8);
            if (throwTempError && ResponseTools.isTemporaryError(response)) {
                throw new TemporaryUnavailableException("ComingSoon service is temporary not available: " + response.getStatusCode());
            } else if (ResponseTools.isNotOK(response)) {
                throw new RuntimeException("ComingSoon request failed: " + response.getStatusCode());
            }
            String xml = response.getContent();
            
            // TITLE
            int beginIndex = xml.indexOf("<h1 itemprop=\"name\"");
            if (beginIndex < 0 ) {
                LOG.error("No title found at ComingSoon page. HTML layout has changed?");
                return false;
            }
                
            String tag = xml.substring(beginIndex, xml.indexOf(">", beginIndex)+1);
            String title = HTMLTools.extractTag(xml, tag, "</h1>").trim();
            if (StringUtils.isBlank(title)) return false;

            final String plot = parsePlot(xml);

            movie.setTitle(WordUtils.capitalizeFully(title))
                .setOriginalTitle(parseTitleOriginal(xml))
                .setPlot(plot)
                .setOutline(plot)
                .setRating(parseRating(xml))
                .setCountries(parseCountries(xml))
                .setStudios(parseStudios(xml))
                .setGenres(parseGenres(xml));

            // RELEASE DATE
            String dateToParse = HTMLTools.stripTags(HTMLTools.extractTag(xml, "<time itemprop=\"datePublished\">", "</time>"));
            Date releaseDate = MetadataTools.parseToDate(dateToParse);
            movie.setReleaseDate(releaseDate);
            
            // YEAR
            String year = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">ANNO</span>:", "</li>")).trim();
            int intYear = NumberUtils.toInt(year, 0); 
            if (intYear > 1900) {
                movie.setYear(intYear);
            } else {
                movie.setYear(MetadataTools.extractYearAsInt(releaseDate));
            } 

            // DIRECTORS
            if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
                parseCredits(movie, xml, ">REGIA</span>:", JobType.DIRECTOR);
            }

            // WRITERS
            if (configService.isCastScanEnabled(JobType.WRITER)) {
                parseCredits(movie, xml, ">SCENEGGIATURA</span>:", JobType.WRITER);
            }

            // SOUND
            if (configService.isCastScanEnabled(JobType.SOUND)) {
                parseCredits(movie, xml, ">MUSICHE</span>:", JobType.SOUND);
            }

            // CAMERA
            if (configService.isCastScanEnabled(JobType.CAMERA)) {
                parseCredits(movie, xml, ">FOTOGRAFIA</span>:", JobType.CAMERA);
            }

            // EDITING
            if (configService.isCastScanEnabled(JobType.EDITING)) {
               parseCredits(movie, xml, ">MONTAGGIO</span>:", JobType.EDITING);
            }
            
            // CAST
            if (configService.isCastScanEnabled(JobType.ACTOR)) {
                movie.addCredits(parseActors(xml));
            }

            return true;
        } catch (IOException ioe) {
            throw new RuntimeException("ComingSoon scanning error", ioe);
        }
    }

    private static void parseCredits(Movie movie, String xml, String startTag, JobType jobType) {
        for (String tag : HTMLTools.extractTags(xml, startTag, "</li>", "<a", "</a>", false)) {
            int beginIndex = tag.indexOf(">");
            if (beginIndex > -1) {
                String name = tag.substring(beginIndex+1);
                
                String sourceId = null;
                beginIndex = tag.indexOf(COMINGSOON_PERSONAGGI);
                if (beginIndex > -1) {
                    beginIndex = tag.indexOf("/", beginIndex+COMINGSOON_PERSONAGGI.length()+1);
                    int endIndex = tag.indexOf("/", beginIndex+1);
                    if (endIndex > beginIndex) {
                        sourceId = tag.substring(beginIndex+1, endIndex);
                    }
                }
                movie.addCredit(new Credit(sourceId, jobType, name));
            }
        }
    }
}
