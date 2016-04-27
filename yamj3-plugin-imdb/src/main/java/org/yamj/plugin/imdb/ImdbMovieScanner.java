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
package org.yamj.plugin.imdb;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import com.omertron.imdbapi.model.ImdbMovieDetails;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.web.HTMLTools;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class ImdbMovieScanner extends AbstractImdbScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ImdbMovieScanner.class);

    @Override
    public boolean isValidMovieId(String movieId) {
        return isValidImdbId(movieId);
    }

    @Override
    public boolean scanMovie(IMovie movie, boolean throwTempError) {
        // get movie id
        final String imdbId = movie.getId(SOURCE_IMDB);
        if (isNoValidImdbId(imdbId)) {
            LOG.debug("IMDb id not available : {}", movie.getTitle());
            return false;
        }

        try {
            LOG.debug("IMDb id available ({}), updating movie", imdbId);
            return updateMovie(movie, imdbId, throwTempError);
        } catch (IOException ioe) {
            LOG.error("IMDb service error: '" + movie.getTitle() + "'", ioe);
            return false;
        }
    }

    private boolean updateMovie(IMovie movie, String imdbId, boolean throwTempError) throws IOException {
        final Locale locale = localeService.getLocale();
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(imdbId, locale, throwTempError);
        Map<String,Integer> top250 = imdbApiWrapper.getTop250(locale, throwTempError);
        
        if (movieDetails == null || StringUtils.isBlank(movieDetails.getImdbId()) || top250 == null) {
            return false;
        }

        // check type change
        if (!"feature".equals(movieDetails.getType())) {
            LOG.warn("Movie '{}' determines a series and no movie", movie.getTitle());
            return false;
        }
        
        // movie details XML is still needed for some parts
        final String xml = imdbApiWrapper.getMovieDetailsXML(imdbId, throwTempError);
        
        movie.setTitle(movieDetails.getTitle());
        movie.setYear(movieDetails.getYear());
        movie.setTagline(movieDetails.getTagline());
        movie.setRating(MetadataTools.parseRating(movieDetails.getRating()));
        movie.setGenres(movieDetails.getGenres());
        movie.setStudios(imdbApiWrapper.getProductionStudios(imdbId));
        movie.setCountries(HTMLTools.extractTags(xml, "Country" + HTML_H4_END, HTML_DIV_END, "<a href=\"", HTML_A_END));

        // ORIGINAL TITLE
        String headerXml = HTMLTools.extractTag(xml, "<h1 class=\"header\">", "</h1>");
        movie.setOriginalTitle(parseOriginalTitle(headerXml));

        // RELEASE DATE
        if (MapUtils.isNotEmpty(movieDetails.getReleaseDate())) {
            final Date releaseDate = MetadataTools.parseToDate(movieDetails.getReleaseDate().get(LITERAL_NORMAL));
            movie.setRelease(null, releaseDate);
        }

        // PLOT
        if (movieDetails.getBestPlot() != null) {
            movie.setPlot(MetadataTools.cleanPlot(movieDetails.getBestPlot().getSummary()));
        }

        // OUTLINE
        if (movieDetails.getPlot() != null) {
            movie.setOutline(MetadataTools.cleanPlot(movieDetails.getPlot().getOutline()));
        }

        // QUOTE
        if (movieDetails.getQuote() != null && CollectionUtils.isNotEmpty(movieDetails.getQuote().getLines())) {
            movie.setQuote(MetadataTools.cleanPlot(movieDetails.getQuote().getLines().get(0).getQuote()));
        }

        // TOP250
        Integer rank = top250.get(imdbId);
        if (rank != null) {
            movie.setTopRank(rank.intValue());
        }

        // CERTIFICATIONS
        imdbApiWrapper.parseCertifications(movie, locale, movieDetails);

        // CAST/CREW
        parseCastCrew(movie);

        // RELEASE INFO
        parseReleasedTitles(movie, locale);

        // AWARDS
        if (configService.getBooleanProperty("imdb.movie.awards", false)) {
            imdbApiWrapper.parseAwards(movie);
        }
        
        return true;
    }
    
}
