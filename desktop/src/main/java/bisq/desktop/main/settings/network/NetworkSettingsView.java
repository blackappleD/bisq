/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.settings.network;

import bisq.desktop.app.BisqApp;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow;
import bisq.desktop.util.GUIUtil;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.Statistic;

import bisq.common.ClockWatcher;
import bisq.common.UserThread;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;
import javafx.geometry.VPos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@FxmlView
public class NetworkSettingsView extends ActivatableViewAndModel<GridPane, Activatable> {

    @FXML
    TitledGroupBg p2pHeader, btcHeader;
    @FXML
    Label btcNodesLabel, bitcoinNodesLabel, localhostBtcNodeInfoLabel;
    @FXML
    InputTextField btcNodesInputTextField;
    @FXML
    TextField onionAddress, totalTrafficTextField;
    @FXML
    Label p2PPeersLabel, bitcoinPeersLabel;
    @FXML
    CheckBox useTorForBtcJCheckBox;
    @FXML
    RadioButton useProvidedNodesRadio, useCustomNodesRadio, usePublicNodesRadio;
    @FXML
    TableView<P2pNetworkListItem> p2pPeersTableView;
    @FXML
    TableView<BitcoinNetworkListItem> bitcoinPeersTableView;
    @FXML
    TableColumn<P2pNetworkListItem, String> onionAddressColumn, connectionTypeColumn, creationDateColumn,
            roundTripTimeColumn, sentBytesColumn, receivedBytesColumn, peerTypeColumn;
    @FXML
    TableColumn<BitcoinNetworkListItem, String> bitcoinPeerAddressColumn, bitcoinPeerVersionColumn,
            bitcoinPeerSubVersionColumn, bitcoinPeerHeightColumn;
    @FXML
    Label reSyncSPVChainLabel;
    @FXML
    AutoTooltipButton reSyncSPVChainButton, openTorSettingsButton;

    private final Preferences preferences;
    private final BtcNodes btcNodes;
    private final FilterManager filterManager;
    private final BisqEnvironment bisqEnvironment;
    private final TorNetworkSettingsWindow torNetworkSettingsWindow;
    private final ClockWatcher clockWatcher;
    private final BSFormatter formatter;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;

    private final ObservableList<P2pNetworkListItem> p2pNetworkListItems = FXCollections.observableArrayList();
    private final SortedList<P2pNetworkListItem> p2pSortedList = new SortedList<>(p2pNetworkListItems);

    private final ObservableList<BitcoinNetworkListItem> bitcoinNetworkListItems = FXCollections.observableArrayList();
    private final SortedList<BitcoinNetworkListItem> bitcoinSortedList = new SortedList<>(bitcoinNetworkListItems);

    private Subscription numP2PPeersSubscription;
    private Subscription bitcoinPeersSubscription;
    private Subscription bitcoinBlockHeightSubscription;
    private Subscription bitcoinBlocksDownloadedSubscription;
    private Subscription nodeAddressSubscription;
    private ChangeListener<Boolean> btcNodesInputTextFieldFocusListener;
    private ToggleGroup bitcoinPeersToggleGroup;
    private BtcNodes.BitcoinNodesOption selectedBitcoinNodesOption;
    private ChangeListener<Toggle> bitcoinPeersToggleGroupListener;
    private ChangeListener<String> btcNodesInputTextFieldListener;
    private ChangeListener<Filter> filterPropertyListener;

    @Inject
    public NetworkSettingsView(WalletsSetup walletsSetup,
                               P2PService p2PService,
                               Preferences preferences,
                               BtcNodes btcNodes,
                               FilterManager filterManager,
                               BisqEnvironment bisqEnvironment,
                               TorNetworkSettingsWindow torNetworkSettingsWindow,
                               ClockWatcher clockWatcher,
                               BSFormatter formatter) {
        super();
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.btcNodes = btcNodes;
        this.filterManager = filterManager;
        this.bisqEnvironment = bisqEnvironment;
        this.torNetworkSettingsWindow = torNetworkSettingsWindow;
        this.clockWatcher = clockWatcher;
        this.formatter = formatter;
    }

    public void initialize() {
        btcHeader.setText(Res.get("settings.net.btcHeader"));
        p2pHeader.setText(Res.get("settings.net.p2pHeader"));
        onionAddress.setPromptText(Res.get("settings.net.onionAddressLabel"));
        btcNodesLabel.setText(Res.get("settings.net.btcNodesLabel"));
        bitcoinPeersLabel.setText(Res.get("settings.net.bitcoinPeersLabel"));
        useTorForBtcJCheckBox.setText(Res.get("settings.net.useTorForBtcJLabel"));
        bitcoinNodesLabel.setText(Res.get("settings.net.bitcoinNodesLabel"));
        bitcoinPeerAddressColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.onionAddressColumn")));
        bitcoinPeerAddressColumn.getStyleClass().add("first-column");
        bitcoinPeerVersionColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.versionColumn")));
        bitcoinPeerSubVersionColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.subVersionColumn")));
        bitcoinPeerHeightColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.heightColumn")));
        localhostBtcNodeInfoLabel.setText(Res.get("settings.net.localhostBtcNodeInfo"));
        if (!bisqEnvironment.isBitcoinLocalhostNodeRunning()) {
            localhostBtcNodeInfoLabel.setVisible(false);
        }
        useProvidedNodesRadio.setText(Res.get("settings.net.useProvidedNodesRadio"));
        useCustomNodesRadio.setText(Res.get("settings.net.useCustomNodesRadio"));
        usePublicNodesRadio.setText(Res.get("settings.net.usePublicNodesRadio"));
        reSyncSPVChainLabel.setText(Res.get("settings.net.reSyncSPVChainLabel"));
        reSyncSPVChainButton.updateText(Res.get("settings.net.reSyncSPVChainButton"));
        p2PPeersLabel.setText(Res.get("settings.net.p2PPeersLabel"));
        onionAddressColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.onionAddressColumn")));
        onionAddressColumn.getStyleClass().add("first-column");
        creationDateColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.creationDateColumn")));
        connectionTypeColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.connectionTypeColumn")));
        totalTrafficTextField.setPromptText(Res.get("settings.net.totalTrafficLabel"));
        roundTripTimeColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.roundTripTimeColumn")));
        sentBytesColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.sentBytesColumn")));
        receivedBytesColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.receivedBytesColumn")));
        peerTypeColumn.setGraphic(new AutoTooltipLabel(Res.get("settings.net.peerTypeColumn")));
        peerTypeColumn.getStyleClass().add("last-column");
        openTorSettingsButton.updateText(Res.get("settings.net.openTorSettingsButton"));

        GridPane.setMargin(bitcoinPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(bitcoinPeersLabel, VPos.TOP);

        GridPane.setMargin(p2PPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(p2PPeersLabel, VPos.TOP);

        bitcoinPeersTableView.setMinHeight(180);
        bitcoinPeersTableView.setPrefHeight(180);
        bitcoinPeersTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        bitcoinPeersTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        bitcoinPeersTableView.getSortOrder().add(bitcoinPeerAddressColumn);
        bitcoinPeerAddressColumn.setSortType(TableColumn.SortType.ASCENDING);


        p2pPeersTableView.setMinHeight(180);
        p2pPeersTableView.setPrefHeight(180);
        p2pPeersTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        p2pPeersTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        p2pPeersTableView.getSortOrder().add(creationDateColumn);
        creationDateColumn.setSortType(TableColumn.SortType.ASCENDING);

        bitcoinPeersToggleGroup = new ToggleGroup();
        useProvidedNodesRadio.setToggleGroup(bitcoinPeersToggleGroup);
        useCustomNodesRadio.setToggleGroup(bitcoinPeersToggleGroup);
        usePublicNodesRadio.setToggleGroup(bitcoinPeersToggleGroup);

        useProvidedNodesRadio.setUserData(BtcNodes.BitcoinNodesOption.PROVIDED);
        useCustomNodesRadio.setUserData(BtcNodes.BitcoinNodesOption.CUSTOM);
        usePublicNodesRadio.setUserData(BtcNodes.BitcoinNodesOption.PUBLIC);

        selectedBitcoinNodesOption = BtcNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        // In case CUSTOM is selected but no custom nodes are set or
        // in case PUBLIC is selected but we blocked it (B2X risk) we revert to provided nodes
        if ((selectedBitcoinNodesOption == BtcNodes.BitcoinNodesOption.CUSTOM &&
                (preferences.getBitcoinNodes() == null || preferences.getBitcoinNodes().isEmpty())) ||
                (selectedBitcoinNodesOption == BtcNodes.BitcoinNodesOption.PUBLIC && isPreventPublicBtcNetwork())) {
            selectedBitcoinNodesOption = BtcNodes.BitcoinNodesOption.PROVIDED;
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
        }
        if (!btcNodes.useProvidedBtcNodes()) {
            selectedBitcoinNodesOption = BtcNodes.BitcoinNodesOption.PUBLIC;
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
        }

        selectBitcoinPeersToggle();
        onBitcoinPeersToggleSelected(false);

        bitcoinPeersToggleGroupListener = (observable, oldValue, newValue) -> {
            selectedBitcoinNodesOption = (BtcNodes.BitcoinNodesOption) newValue.getUserData();
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
            onBitcoinPeersToggleSelected(true);
        };

        btcNodesInputTextFieldListener = (observable, oldValue, newValue) -> preferences.setBitcoinNodes(newValue);
        btcNodesInputTextFieldFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue)
                showShutDownPopup();
        };
        filterPropertyListener = (observable, oldValue, newValue) -> {
            applyPreventPublicBtcNetwork();
        };

        //TODO sorting needs other NetworkStatisticListItem as columns type
       /* creationDateColumn.setComparator((o1, o2) ->
                o1.statistic.getCreationDate().compareTo(o2.statistic.getCreationDate()));
        sentBytesColumn.setComparator((o1, o2) ->
                ((Integer) o1.statistic.getSentBytes()).compareTo(((Integer) o2.statistic.getSentBytes())));
        receivedBytesColumn.setComparator((o1, o2) ->
                ((Integer) o1.statistic.getReceivedBytes()).compareTo(((Integer) o2.statistic.getReceivedBytes())));*/
    }

    @Override
    public void activate() {
        bitcoinPeersToggleGroup.selectedToggleProperty().addListener(bitcoinPeersToggleGroupListener);

        if (filterManager.getFilter() != null)
            applyPreventPublicBtcNetwork();

        filterManager.filterProperty().addListener(filterPropertyListener);

        useTorForBtcJCheckBox.setSelected(preferences.getUseTorForBitcoinJ());
        useTorForBtcJCheckBox.setOnAction(event -> {
            boolean selected = useTorForBtcJCheckBox.isSelected();
            if (selected != preferences.getUseTorForBitcoinJ()) {
                new Popup<>().information(Res.get("settings.net.needRestart"))
                        .actionButtonText(Res.get("shared.applyAndShutDown"))
                        .onAction(() -> {
                            preferences.setUseTorForBitcoinJ(selected);
                            UserThread.runAfter(BisqApp.getShutDownHandler()::run, 500, TimeUnit.MILLISECONDS);
                        })
                        .closeButtonText(Res.get("shared.cancel"))
                        .onClose(() -> useTorForBtcJCheckBox.setSelected(!selected))
                        .show();
            }
        });

        reSyncSPVChainButton.setOnAction(event -> GUIUtil.reSyncSPVChain(walletsSetup, preferences));

        bitcoinPeersSubscription = EasyBind.subscribe(walletsSetup.connectedPeersProperty(),
                connectedPeers -> updateBitcoinPeersTable());

        bitcoinBlocksDownloadedSubscription = EasyBind.subscribe(walletsSetup.blocksDownloadedFromPeerProperty(),
                peer -> updateBitcoinPeersTable());

        bitcoinBlockHeightSubscription = EasyBind.subscribe(walletsSetup.chainHeightProperty(),
                chainHeight -> updateBitcoinPeersTable());

        nodeAddressSubscription = EasyBind.subscribe(p2PService.getNetworkNode().nodeAddressProperty(),
                nodeAddress -> onionAddress.setText(nodeAddress == null ?
                        Res.get("settings.net.notKnownYet") :
                        nodeAddress.getFullAddress()));
        numP2PPeersSubscription = EasyBind.subscribe(p2PService.getNumConnectedPeers(), numPeers -> updateP2PTable());
        totalTrafficTextField.textProperty().bind(EasyBind.combine(Statistic.totalSentBytesProperty(),
                Statistic.totalReceivedBytesProperty(),
                (sent, received) -> Res.get("settings.net.sentReceived",
                        BSFormatter.formatBytes((long) sent),
                        BSFormatter.formatBytes((long) received))));

        bitcoinSortedList.comparatorProperty().bind(bitcoinPeersTableView.comparatorProperty());
        bitcoinPeersTableView.setItems(bitcoinSortedList);

        p2pSortedList.comparatorProperty().bind(p2pPeersTableView.comparatorProperty());
        p2pPeersTableView.setItems(p2pSortedList);

        btcNodesInputTextField.setText(preferences.getBitcoinNodes());
        btcNodesInputTextField.setPromptText(Res.get("settings.net.ips"));

        btcNodesInputTextField.textProperty().addListener(btcNodesInputTextFieldListener);
        btcNodesInputTextField.focusedProperty().addListener(btcNodesInputTextFieldFocusListener);

        openTorSettingsButton.setOnAction(e -> torNetworkSettingsWindow.show());
    }

    @Override
    public void deactivate() {
        bitcoinPeersToggleGroup.selectedToggleProperty().removeListener(bitcoinPeersToggleGroupListener);
        filterManager.filterProperty().removeListener(filterPropertyListener);

        useTorForBtcJCheckBox.setOnAction(null);

        if (nodeAddressSubscription != null)
            nodeAddressSubscription.unsubscribe();

        if (bitcoinPeersSubscription != null)
            bitcoinPeersSubscription.unsubscribe();

        if (bitcoinBlockHeightSubscription != null)
            bitcoinBlockHeightSubscription.unsubscribe();

        if (bitcoinBlocksDownloadedSubscription != null)
            bitcoinBlocksDownloadedSubscription.unsubscribe();

        if (numP2PPeersSubscription != null)
            numP2PPeersSubscription.unsubscribe();

        totalTrafficTextField.textProperty().unbind();

        bitcoinSortedList.comparatorProperty().unbind();
        p2pSortedList.comparatorProperty().unbind();
        p2pPeersTableView.getItems().forEach(P2pNetworkListItem::cleanup);
        btcNodesInputTextField.focusedProperty().removeListener(btcNodesInputTextFieldFocusListener);
        btcNodesInputTextField.textProperty().removeListener(btcNodesInputTextFieldListener);

        openTorSettingsButton.setOnAction(null);
    }

    private boolean isPreventPublicBtcNetwork() {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().isPreventPublicBtcNetwork();
    }

    private void selectBitcoinPeersToggle() {
        switch (selectedBitcoinNodesOption) {
            case CUSTOM:
                bitcoinPeersToggleGroup.selectToggle(useCustomNodesRadio);
                break;
            case PUBLIC:
                bitcoinPeersToggleGroup.selectToggle(usePublicNodesRadio);
                break;
            default:
            case PROVIDED:
                bitcoinPeersToggleGroup.selectToggle(useProvidedNodesRadio);
                break;
        }
    }

    private void showShutDownPopup() {
        new Popup<>()
                .information(Res.get("settings.net.needRestart"))
                .closeButtonText(Res.get("shared.cancel"))
                .useShutDownButton()
                .show();
    }

    private void onBitcoinPeersToggleSelected(boolean calledFromUser) {
        boolean bitcoinLocalhostNodeRunning = bisqEnvironment.isBitcoinLocalhostNodeRunning();
        useTorForBtcJCheckBox.setDisable(bitcoinLocalhostNodeRunning);
        bitcoinNodesLabel.setDisable(bitcoinLocalhostNodeRunning);
        btcNodesLabel.setDisable(bitcoinLocalhostNodeRunning);
        btcNodesInputTextField.setDisable(bitcoinLocalhostNodeRunning);
        useProvidedNodesRadio.setDisable(bitcoinLocalhostNodeRunning || !btcNodes.useProvidedBtcNodes());
        useCustomNodesRadio.setDisable(bitcoinLocalhostNodeRunning);
        usePublicNodesRadio.setDisable(bitcoinLocalhostNodeRunning || isPreventPublicBtcNetwork());

        switch (selectedBitcoinNodesOption) {
            case CUSTOM:
                btcNodesInputTextField.setDisable(false);
                btcNodesLabel.setDisable(false);
                if (calledFromUser && !btcNodesInputTextField.getText().isEmpty()) {
                    if (isPreventPublicBtcNetwork()) {
                        new Popup<>().warning(Res.get("settings.net.warn.useCustomNodes.B2XWarning"))
                                .onAction(() -> {
                                    UserThread.runAfter(this::showShutDownPopup, 300, TimeUnit.MILLISECONDS);
                                }).show();
                    } else {
                        showShutDownPopup();
                    }
                }
                break;
            case PUBLIC:
                btcNodesInputTextField.setDisable(true);
                btcNodesLabel.setDisable(true);
                if (calledFromUser)
                    new Popup<>()
                            .warning(Res.get("settings.net.warn.usePublicNodes"))
                            .actionButtonText(Res.get("settings.net.warn.usePublicNodes.useProvided"))
                            .onAction(() -> {
                                UserThread.runAfter(() -> {
                                    selectedBitcoinNodesOption = BtcNodes.BitcoinNodesOption.PROVIDED;
                                    preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
                                    selectBitcoinPeersToggle();
                                    onBitcoinPeersToggleSelected(false);
                                }, 300, TimeUnit.MILLISECONDS);
                            })
                            .closeButtonText(Res.get("settings.net.warn.usePublicNodes.usePublic"))
                            .onClose(() -> {
                                UserThread.runAfter(this::showShutDownPopup, 300, TimeUnit.MILLISECONDS);
                            })
                            .show();
                break;
            default:
            case PROVIDED:
                if (btcNodes.useProvidedBtcNodes()) {
                    btcNodesInputTextField.setDisable(true);
                    btcNodesLabel.setDisable(true);
                    if (calledFromUser)
                        showShutDownPopup();
                } else {
                    selectedBitcoinNodesOption = BtcNodes.BitcoinNodesOption.PUBLIC;
                    preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
                    selectBitcoinPeersToggle();
                    onBitcoinPeersToggleSelected(false);
                }
                break;
        }
    }


    private void applyPreventPublicBtcNetwork() {
        final boolean preventPublicBtcNetwork = isPreventPublicBtcNetwork();
        usePublicNodesRadio.setDisable(bisqEnvironment.isBitcoinLocalhostNodeRunning() || preventPublicBtcNetwork);
        if (preventPublicBtcNetwork && selectedBitcoinNodesOption == BtcNodes.BitcoinNodesOption.PUBLIC) {
            selectedBitcoinNodesOption = BtcNodes.BitcoinNodesOption.PROVIDED;
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
            selectBitcoinPeersToggle();
            onBitcoinPeersToggleSelected(false);
        }
    }

    private void updateP2PTable() {
        p2pPeersTableView.getItems().forEach(P2pNetworkListItem::cleanup);
        p2pNetworkListItems.clear();
        p2pNetworkListItems.setAll(p2PService.getNetworkNode().getAllConnections().stream()
                .map(connection -> new P2pNetworkListItem(connection, clockWatcher, formatter))
                .collect(Collectors.toList()));
    }

    private void updateBitcoinPeersTable() {
        bitcoinNetworkListItems.clear();
        bitcoinNetworkListItems.setAll(walletsSetup.getPeerGroup().getConnectedPeers().stream()
                .map(BitcoinNetworkListItem::new)
                .collect(Collectors.toList()));
    }
}

