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

import com.omertron.imdbapi.model.ImdbEncodingFormat;
import com.omertron.imdbapi.model.ImdbMovieDetails;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.model.type.ContainerType;
import org.yamj.plugin.api.trailer.MovieTrailerScanner;
import org.yamj.plugin.api.trailer.SeriesTrailerScanner;
import org.yamj.plugin.api.trailer.TrailerDTO;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class ImdbTrailerScanner extends AbstractImdbScanner implements MovieTrailerScanner, SeriesTrailerScanner {

    @Override
    public List<TrailerDTO> scanForTrailer(IMovie movie) {
        String imdbId = getMovieId(movie, false);
        return getTrailerDTOS(imdbId);
    }

    @Override
    public List<TrailerDTO> scanForTrailer(ISeries series) {
        String imdbId = getSeriesId(series, false);
        return getTrailerDTOS(imdbId);
    }
    
    private List<TrailerDTO> getTrailerDTOS(String imdbId) {
        if (StringUtils.isBlank(imdbId)) { 
            return null; //NOSONAR
        }
        
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(imdbId, Locale.US, false);
        if (movieDetails == null || movieDetails.getTrailer() == null || MapUtils.isEmpty(movieDetails.getTrailer().getEncodings())) {
            return null; //NOSONAR
        }
        
        String url = null;
        int prio = 1000;
        
        for (ImdbEncodingFormat format : movieDetails.getTrailer().getEncodings().values()) {
            switch(format.getFormat()) {
                case "HD 720":
                   if (prio > 10) {
                       prio = 10;
                       url = format.getUrl();
                   }
                   break;
                case "HD 480p":
                    if (prio > 20) {
                        prio = 20;
                        url = format.getUrl();
                    }
                    break;
                case "H.264 Fire 600":
                    if (prio > 30) {
                        prio = 30;
                        url = format.getUrl();
                    }
                    break;
                default:
                    if (prio > 100) {
                        prio = 100;
                        url = format.getUrl();
                    }
                    break;
            }
        }

        if (url == null) {
            return null; //NOSONAR
        }
        
        TrailerDTO dto = new TrailerDTO(SOURCE_IMDB, ContainerType.MP4, url, movieDetails.getTrailer().getTitle(), imdbId); 
        return Collections.singletonList(dto);
    }
}