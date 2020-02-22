/*
 * Copyright (C) 2018  Anthony Chomienne, anthony@mob-dev.fr
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.eu.exodus_privacy.exodusprivacy.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.eu.exodus_privacy.exodusprivacy.R;
import org.eu.exodus_privacy.exodusprivacy.adapters.ApplicationListAdapter;
import org.eu.exodus_privacy.exodusprivacy.adapters.ApplicationViewModel;
import org.eu.exodus_privacy.exodusprivacy.databinding.ApplistBinding;
import org.eu.exodus_privacy.exodusprivacy.listener.NetworkListener;
import org.eu.exodus_privacy.exodusprivacy.manager.DatabaseManager;
import org.eu.exodus_privacy.exodusprivacy.manager.NetworkManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AppListFragment extends Fragment implements ComputeAppListTask.Listener {

    private @Nullable
    PackageManager packageManager;
    private NetworkListener networkListener;
    private ApplicationListAdapter.OnAppClickListener onAppClickListener;
    private boolean startupRefresh;
    private ApplistBinding applistBinding;
    private @Nullable ApplicationListAdapter adapter;
    private List<ApplicationViewModel> applications;

    public static AppListFragment newInstance(NetworkListener networkListener, ApplicationListAdapter.OnAppClickListener appClickListener) {
        AppListFragment fragment = new AppListFragment();
        fragment.setNetworkListener(networkListener);
        fragment.setOnAppClickListener(appClickListener);
        fragment.startupRefresh = true;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applications = new ArrayList<>();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        applistBinding = DataBindingUtil.inflate(inflater,R.layout.applist,container,false);
        return applistBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(applistBinding == null)
            return;
        Context context = applistBinding.getRoot().getContext();
        applistBinding.swipeRefresh.setOnRefreshListener(() -> startRefresh());
        if (packageManager == null)
            packageManager = context.getPackageManager();

        applistBinding.appList.setLayoutManager(new LinearLayoutManager(context));
        if (packageManager != null) {
            applistBinding.noPackageManager.setVisibility(View.GONE);
            adapter = new ApplicationListAdapter(context, onAppClickListener);
            applistBinding.appList.setAdapter(adapter);
            onAppsComputed(applications);
            displayAppListAsync();
        } else {
            applistBinding.noPackageManager.setVisibility(View.VISIBLE);
        }
    }

    public void startRefresh(){
        if(applistBinding == null)
            return;
        applistBinding.layoutProgress.setVisibility(View.VISIBLE);
        applistBinding.swipeRefresh.setRefreshing(true);
        List<PackageInfo> packageInstalled = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        ArrayList<String> packageList = new ArrayList<>();
        for(PackageInfo pkgInfo : packageInstalled)
            packageList.add(pkgInfo.packageName);

        NetworkManager.getInstance().getReports(applistBinding.getRoot().getContext(),networkListener,packageList);
    }

    public void updateComplete() {
        if(applistBinding != null) {
            applistBinding.layoutProgress.setVisibility(View.GONE);
            applistBinding.swipeRefresh.setRefreshing(false);
            displayAppListAsync();
        }
    }

    public void setNetworkListener(NetworkListener listener) {
        this.networkListener = new NetworkListener() {
            @Override
            public void onSuccess() {
                listener.onSuccess();
            }

            @Override
            public void onError(String error) {
                listener.onError(error);
            }

            public void onProgress(int resourceId, int progress, int maxProgress) {
                updateProgress(resourceId, progress, maxProgress);
            }
        };
    }

    private void updateProgress(int resourceId, int progress, int maxProgress) {
        Activity activity = getActivity();
        if(activity == null)
            return;
        activity.runOnUiThread(() -> {
            if (applistBinding == null)
                return;
            if(maxProgress > 0)
                applistBinding.statusProgress.setText(getString(resourceId)+" "+progress+"/"+maxProgress);
            else
                applistBinding.statusProgress.setText(getString(resourceId));
            applistBinding.progress.setMax(maxProgress);
            applistBinding.progress.setProgress(progress);
        });

    }

    public void setOnAppClickListener(ApplicationListAdapter.OnAppClickListener onAppClickListener) {
        this.onAppClickListener = onAppClickListener;
    }


    @Override
    public void onAttach(Activity context) {
        super.onAttach(context);
        packageManager = context.getPackageManager();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        packageManager = null;
    }

    public void filter(String filter){
        if(adapter != null) {
            adapter.filter(filter);
        }
    }

    private void displayAppListAsync() {
        applistBinding.noAppFound.setVisibility(View.GONE);
        if (applications.isEmpty()) {
            applistBinding.retrieveApp.setVisibility(View.VISIBLE);
            applistBinding.logo.setVisibility(View.VISIBLE);
        }

        new ComputeAppListTask(
                new WeakReference<>(packageManager),
                new WeakReference<>(DatabaseManager.getInstance(getActivity())),
                new WeakReference<>(this)
       ).execute();
    }

    @Override
    public void onAppsComputed(List<ApplicationViewModel> apps) {
        this.applications = apps;
        applistBinding.retrieveApp.setVisibility(View.GONE);
        applistBinding.logo.setVisibility(View.GONE);
        applistBinding.noAppFound.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        if(adapter != null) {
            adapter.displayAppList(apps);
        }
        if(!apps.isEmpty()) {
            if(startupRefresh) {
                startRefresh();
                startupRefresh = false;
            }
        }
    }
}
