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
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.DigestedResponse;
import org.yamj.api.common.tools.ResponseTools;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.model.type.JobType;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.TemporaryUnavailableException;
import ro.fortsoft.pf4j.Extension;
 
@Extension
public final class ComingSoonMovieScanner extends AbstractComingSoonScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ComingSoonMovieScanner.class);
    private static final String COMINGSOON_MOVIE_URL = "film/scheda/?";
    private static final String COMINGSOON_PERSONAGGI = "personaggi/";

    @Override
    public boolean isValidMovieId(String movieId) {
        return isValidComingSoonId(movieId);
    }

    @Override
    public boolean scanMovie(IMovie movie, boolean throwTempError) {
        final String comingSoonId = movie.getId(SCANNER_NAME);
        if (isNoValidComingSoonId(comingSoonId)) {
            LOG.debug("ComingSoon id not available '{}'", movie.getTitle());
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
            if (StringUtils.isBlank(title)) {
                return false;
            }

            final String plot = parsePlot(xml);

            movie.setTitle(WordUtils.capitalizeFully(title));
            movie.setOriginalTitle(parseTitleOriginal(xml));
            movie.setPlot(plot);
            movie.setOutline(plot);
            movie.setRating(parseRating(xml));
            movie.setCountries(parseCountries(xml));
            movie.setStudios(parseStudios(xml));
            movie.setGenres(parseGenres(xml));

            // RELEASE DATE
            String dateToParse = HTMLTools.stripTags(HTMLTools.extractTag(xml, "<time itemprop=\"datePublished\">", "</time>"));
            Date releaseDate = MetadataTools.parseToDate(dateToParse);
            movie.setRelease(null, releaseDate);
            
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
                parseActors(movie, xml);
            }

            return true;
        } catch (IOException ioe) {
            throw new RuntimeException("ComingSoon scanning error", ioe);
        }
    }

    private static void parseCredits(IMovie movie, String xml, String startTag, JobType jobType) {
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
                movie.addCredit(sourceId, jobType, name);
            }
        }
    }
    
    protected static void parseActors(IMovie movie, String xml) {
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
            
            final String posterURL = HTMLTools.extractTag(tag, "<img src=\"", "\"");
            if (posterURL.contains("http")) {
                movie.addCredit(sourceId, JobType.ACTOR, name, role, posterURL.replace("_ico.jpg", ".jpg"));
            } else {
                movie.addCredit(sourceId, JobType.ACTOR, name, role);
            }
        }
    }
}
