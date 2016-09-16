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
package org.yamj.plugin.moviemeter;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.metadata.MetadataTools.isOriginalTitleScannable;
import static org.yamj.plugin.moviemeter.MovieMeterPlugin.SCANNER_NAME;

import org.apache.commons.lang3.StringUtils;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.service.PluginConfigService;

public abstract class AbstractMovieMeterScanner implements NeedsConfigService {

    protected PluginConfigService configService;
    protected MovieMeterApiWrapper movieMeterApiWrapper;
    
    public String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public void setConfigService(PluginConfigService configService) {
        this.configService = configService;
        // also set the API wrapper
        this.movieMeterApiWrapper = MovieMeterPlugin.getMovieMeterApiWrapper();
    }
    
    public boolean isValidMovieId(String movieId) {
        return StringUtils.isNumeric(movieId);
    }

    public String getMovieId(IMovie movie, boolean throwTempError) {
        String movieMeterId = movie.getId(SCANNER_NAME);
        if (isValidMovieId(movieMeterId)) {
            return movieMeterId;
        }
        
        // try to get the MovieMeter ID using the IMDB ID
        String imdbId = movie.getId(SOURCE_IMDB);
        if (StringUtils.isNotBlank(imdbId)) {
            movieMeterId = movieMeterApiWrapper.getMovieIdByIMDbId(imdbId, throwTempError);
        }

        // try to get the MovieMeter ID using title and year
        if (!isValidMovieId(movieMeterId)) {
            movieMeterId = movieMeterApiWrapper.getMovieIdByTitleAndYear(movie.getTitle(), movie.getYear(), throwTempError);
        }

        // try to get the MovieMeter ID using original title and year
        if (!isValidMovieId(movieMeterId) && isOriginalTitleScannable(movie)) {
            movieMeterId = movieMeterApiWrapper.getMovieIdByTitleAndYear(movie.getOriginalTitle(), movie.getYear(), throwTempError);
        }

        if (isValidMovieId(movieMeterId)) {
            movie.addId(SCANNER_NAME, movieMeterId);
            return movieMeterId;
        }
        
        return null;
    }
}
