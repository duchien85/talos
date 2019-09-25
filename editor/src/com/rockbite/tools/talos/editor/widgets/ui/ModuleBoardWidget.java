package com.rockbite.tools.talos.editor.widgets.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Bezier;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.kotcrab.vis.ui.FocusManager;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.rockbite.tools.talos.TalosMain;
import com.rockbite.tools.talos.editor.Curve;
import com.rockbite.tools.talos.editor.ParticleEmitterWrapper;
import com.rockbite.tools.talos.editor.NodeStage;
import com.rockbite.tools.talos.editor.serialization.ConnectionData;
import com.rockbite.tools.talos.editor.serialization.EmitterData;
import com.rockbite.tools.talos.editor.wrappers.*;
import com.rockbite.tools.talos.runtime.*;
import com.rockbite.tools.talos.runtime.modules.*;
import com.rockbite.tools.talos.runtime.modules.Module;

import java.util.Comparator;

public class ModuleBoardWidget extends WidgetGroup {

    ShapeRenderer shapeRenderer;

    public ObjectMap<ParticleEmitterWrapper, Array<ModuleWrapper>> moduleWrappers = new ObjectMap<>();
    public ObjectMap<ParticleEmitterWrapper, Array<NodeConnection>> nodeConnections = new ObjectMap<>();
    private ParticleEmitterWrapper currentEmitterWrapper;
    private ModuleWrapper selectedWrapper;

    Group moduleContainer = new Group();

    Vector2 gridPos = new Vector2();
    Vector2 tmp = new Vector2();
    Vector2 tmp2 = new Vector2();
    Vector2 prev = new Vector2();


    private Curve activeCurve;

    private Bezier<Vector2> bezier = new Bezier<>();
    private Vector2[] curvePoints = new Vector2[4];



    private NodeStage mainStage;

    public ModuleBoardWidget(NodeStage mainStage) {
        super();
        this.mainStage = mainStage;

        curvePoints[0] = new Vector2();
        curvePoints[1] = new Vector2();
        curvePoints[2] = new Vector2();
        curvePoints[3] = new Vector2();

        registerWrappers();

        addActor(moduleContainer);

        shapeRenderer = new ShapeRenderer();

        addListener(new ClickListener() {
            Vector2 prev = new Vector2();
            Vector2 tmp = new Vector2();

            public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                prev.set(x, y);
                return true;
            }
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                if(event.isHandled()) return;
                tmp.set(x, y);
                tmp.sub(prev);

                //setX(getX() - tmp.x);
                //setY(getY() - tmp.y);
                gridPos.x += tmp.x;
                gridPos.y += tmp.y;

                prev.set(x, y);
                super.touchDragged(event, x, y, pointer);
            }
            public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
                super.touchUp(event, x, y, pointer, button);

                if(!event.isHandled() && button == 0) {
                    FocusManager.resetFocus(getStage());

                    if(selectedWrapper != null) {
                        selectedWrapper.setBackground("window");
                        selectedWrapper = null;
                    }
                }

                if(button == 1 && !event.isHandled()) {
                    showPopup();
                }
            }

            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if(event.isHandled()) return super.keyUp(event, keycode);
                if(keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) {
                    deleteSelectedWrapper();
                }
                return super.keyUp(event, keycode);
            }
        });
    }

    public Array<NodeConnection> getCurrentConnections() {
        Array<NodeConnection> arr =  nodeConnections.get(currentEmitterWrapper);
        if(arr == null) {
            arr = new Array<>();
            nodeConnections.put(currentEmitterWrapper, arr);
        }

        return arr;
    }

    public Array<ModuleWrapper> getModuleWrappers() {
        Array<ModuleWrapper> arr = moduleWrappers.get(currentEmitterWrapper);
        if(arr == null) {
            arr = new Array<>();
            moduleWrappers.put(currentEmitterWrapper, arr);
        }

        return arr;
    }

    public NodeConnection findConnection(ModuleWrapper moduleWrapper, boolean isInput, int key) {
        NodeConnection nodeToFind =  null;
        for(NodeConnection nodeConnection: getCurrentConnections()) {
            if((isInput && nodeConnection.toSlot == key && moduleWrapper == nodeConnection.toModule) ||
                    (!isInput && nodeConnection.fromSlot == key && moduleWrapper == nodeConnection.fromModule)) {
                // found the node let's remove it
                nodeToFind = nodeConnection;
            }
        }

        return nodeToFind;
    }

    public void removeConnection(NodeConnection connection) {
        getCurrentConnections().removeValue(connection, true);

        connection.fromModule.setSlotInactive(connection.fromSlot, false);
        connection.toModule.setSlotInactive(connection.toSlot, true);

        mainStage.getCurrentModuleGraph().removeNode(connection.fromModule.getModule(), connection.fromSlot, false);
        mainStage.getCurrentModuleGraph().removeNode(connection.toModule.getModule(), connection.toSlot, true);
    }

    public void selectWrapper(ModuleWrapper selectWrapper) {
        for(ModuleWrapper wrapper : getModuleWrappers()) {
            if(selectWrapper == wrapper) {
                wrapper.setBackground("window-blue");
                selectedWrapper = wrapper;
            } else {
                wrapper.setBackground("window");
            }
        }
    }

    public void setCurrentEmitter(ParticleEmitterWrapper currentEmitterWrapper) {
        this.currentEmitterWrapper = currentEmitterWrapper;

        moduleContainer.clearChildren();

        if(this.currentEmitterWrapper == null) return;

        for (ModuleWrapper wrapper : getModuleWrappers()) {
                moduleContainer.addActor(wrapper);
        }
    }

    public void removeEmitter(ParticleEmitterWrapper wrapper) {
        moduleWrappers.remove(wrapper);
        nodeConnections.remove(wrapper);
    }

    public void clearAll() {
        moduleWrappers.clear();
        nodeConnections.clear();
    }

    public void fileDrop(String[] paths, float x, float y) {
        tmp.set(x, y);
        (getStage().getViewport()).unproject(tmp);

        for(ModuleWrapper wrapper: getModuleWrappers()) {
            tmp2.set(tmp);
            wrapper.stageToLocalCoordinates(tmp2);

            if(wrapper.hit(tmp2.x, tmp2.y, false) != null) {
                wrapper.fileDrop(paths, tmp2.x, tmp2.y);
            }
        }
    }


    public void loadEmitterToBoard(ParticleEmitterWrapper emitterWrapper, EmitterData emitterData) {
        IntMap<ModuleWrapper> map = new IntMap<>();
        if(!moduleWrappers.containsKey(emitterWrapper)) {
            moduleWrappers.put(emitterWrapper, new Array<ModuleWrapper>());
        }

        for(ModuleWrapper wrapper: emitterData.modules) {
            moduleWrappers.get(emitterWrapper).add(wrapper);
            wrapper.setModule(wrapper.getModule());
            wrapper.setBoard(this);
            map.put(wrapper.getId(), wrapper);
        }
        for(ConnectionData connectionData: emitterData.connections) {
            // make connections based on ids
            makeConnection(map.get(connectionData.moduleFrom), map.get(connectionData.moduleTo), connectionData.slotFrom, connectionData.slotTo);
        }
    }

    public class NodeConnection {
        public ModuleWrapper fromModule;
        public ModuleWrapper toModule;
        public int fromSlot;
        public int toSlot;
    }

    private void registerWrappers() {
        WrapperRegistry.reg(EmitterModule.class, EmitterModuleWrapper.class);
        WrapperRegistry.reg(InterpolationModule.class, InterpolationWrapper.class);
        WrapperRegistry.reg(InputModule.class, InputModuleWrapper.class);
        WrapperRegistry.reg(ParticleModule.class, ParticleModuleWrapper.class);
        WrapperRegistry.reg(StaticValueModule.class, StaticValueModuleWrapper.class);
        WrapperRegistry.reg(RandomRangeModule.class, RandomRangeModuleWrapper.class);
        WrapperRegistry.reg(MixModule.class, MixModuleWrapper.class);
        WrapperRegistry.reg(MathModule.class, MathModuleWrapper.class);
        WrapperRegistry.reg(CurveModule.class, CurveModuleWrapper.class);
        WrapperRegistry.reg(Vector2Module.class, Vector2ModuleWrapper.class);
        WrapperRegistry.reg(ColorModule.class, ColorModuleWrapper.class);
        WrapperRegistry.reg(DynamicRangeModule.class, DynamicRangeModuleWrapper.class);
        WrapperRegistry.reg(ScriptModule.class, ScriptModuleWrapper.class);
        WrapperRegistry.reg(GradientColorModule.class, GradientColorModuleWrapper.class);
        WrapperRegistry.reg(TextureModule.class, TextureModuleWrapper.class);
        WrapperRegistry.reg(EmConfigModule.class, EmConfigModuleWrapper.class);
        WrapperRegistry.reg(OffsetModule.class, OffsetModuleWrapper.class);
    }

    public void showPopup() {
        ParticleEmitterDescriptor moduleGraph = getModuleGraph();

        if(moduleGraph == null) return;

        PopupMenu menu = new PopupMenu();
        Array<Class> temp = new Array<>();
        for (Class registeredModule : ParticleEmitterDescriptor.getRegisteredModules()) {
            temp.add(registeredModule);
        }
        temp.sort(new Comparator<Class>() {
            @Override
            public int compare (Class o1, Class o2) {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        for(final Class clazz : temp) {
            String className = clazz.getSimpleName();
            MenuItem menuItem = new MenuItem(className);
            menu.addItem(menuItem);

            final Vector2 vec = new Vector2(Gdx.input.getX(), Gdx.input.getY());
            (TalosMain.Instance().UIStage().getStage().getViewport()).unproject(vec);

            menu.showMenu(TalosMain.Instance().UIStage().getStage(), vec.x, vec.y);

            menuItem.addListener(new ClickListener() {
                @Override
                public void clicked (InputEvent event, float x, float y) {
                    createModule(clazz, vec.x, vec.y);
                }
            });
        }
    }

    private void deleteSelectedWrapper() {
        if(selectedWrapper != null) {
            getModuleWrappers().removeValue(selectedWrapper, true);
            for(int i = getCurrentConnections().size-1; i >= 0; i--) {
                if(getCurrentConnections().get(i).toModule == selectedWrapper || getCurrentConnections().get(i).fromModule == selectedWrapper) {
                    removeConnection(getCurrentConnections().get(i));
                }
            }
            mainStage.getCurrentModuleGraph().removeModule(selectedWrapper.getModule());
            moduleContainer.removeActor(selectedWrapper);
        }
    }

    public ModuleWrapper createModule (Class<? extends Module> clazz, float x, float y) {
        final Module module;
        try {
            module = ClassReflection.newInstance(clazz);
            module.setModuleGraph(TalosMain.Instance().Project().getCurrentModuleGraph());

            if (TalosMain.Instance().Project().getCurrentModuleGraph().addModule(module)) {
                return createModuleWrapper(module, x, y);
            } else {
                System.out.println("Did not create module: " + clazz.getSimpleName());
                return null;
            }
        } catch (ReflectionException e) {
            throw new GdxRuntimeException(e);
        }
    }

    public <T extends Module> ModuleWrapper createModuleWrapper (T module, float x, float y) {
        ModuleWrapper<T> moduleWrapper = null;

        if (module == null) return null;

        Class<T> moduleClazz = (Class<T>)module.getClass();

        try {
            moduleWrapper = ClassReflection.newInstance(WrapperRegistry.get(moduleClazz));
            int id = getUniqueIdForModuleWrapper();
            moduleWrapper.setId(id);

            moduleWrapper.setModule(module);
            moduleWrapper.setBoard(this);

            tmp.set(x, Gdx.graphics.getHeight() - y);
            moduleContainer.screenToLocalCoordinates(tmp);

            moduleWrapper.setPosition(tmp.x - moduleWrapper.getWidth()/2f, tmp.y - moduleWrapper.getHeight()/2f);
            getModuleWrappers().add(moduleWrapper);
            moduleContainer.addActor(moduleWrapper);

            selectWrapper(moduleWrapper);
        } catch (ReflectionException e) {
            e.printStackTrace();
        }


        return moduleWrapper;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        batch.end();
        shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawCurves();
        shapeRenderer.end();
        batch.begin();

        super.draw(batch, parentAlpha);
    }

    private void drawCurves() {
        if(currentEmitterWrapper == null) return;

        // draw active curve
        if(activeCurve != null) {
            shapeRenderer.setColor(0, 203/255f, 124/255f, 1f);
            drawCurve(activeCurve.getFrom().x, activeCurve.getFrom().y, activeCurve.getTo().x, activeCurve.getTo().y);
        }

        shapeRenderer.setColor(1, 1, 1, 0.4f);
        // draw nodes
        for(NodeConnection connection: getCurrentConnections()) {
            connection.fromModule.getOutputSlotPos(connection.fromSlot, tmp);
            float x = tmp.x;
            float y = tmp.y;
            connection.toModule.getInputSlotPos(connection.toSlot, tmp);
            float toX = tmp.x;
            float toY = tmp.y;
            drawCurve(x, y, toX, toY);
        }
    }

    private void drawCurve(float x, float y, float toX, float toY) {
        //shapeRenderer.setColor(1, 1, 1, 1f);
        //shapeRenderer.rectLine(x, y, toX, toY, 2f);

        float minOffset = 10f;
        float maxOffset = 150f;

        float deltaX = Math.abs(toX - x);
        if(deltaX > maxOffset) deltaX = maxOffset;
        deltaX = deltaX/maxOffset;

        float offset = minOffset + (maxOffset-minOffset) * deltaX;

        curvePoints[0].set(x, y);
        curvePoints[1].set(x+offset, y);
        curvePoints[2].set(toX - offset, toY);
        curvePoints[3].set(toX + 20f, toY);

        bezier.set(curvePoints, 0, curvePoints.length);

        float resolution = 1f/20f;

        for(float i = 0; i < 1f; i+=resolution) {
            bezier.valueAt(tmp, i);
            if(i > 0) {
                shapeRenderer.rectLine(prev.x, prev.y, tmp.x, tmp.y, 2f);
            }
            prev.set(tmp);
        }
    }

    @Override
    public void act(float delta) {

        //center pos
        tmp.x = gridPos.x+getStage().getWidth()/2f;
        tmp.y = gridPos.y+getStage().getHeight()/2f;

        // now we need to figure out how to project that pos from stage to this widget
        this.stageToLocalCoordinates(tmp);

        moduleContainer.setPosition(tmp.x, tmp.y);

        super.act(delta);
    }

    @Override
    public void layout() {
        super.layout();
    }

    public ParticleEmitterDescriptor getModuleGraph() {
        return mainStage.getCurrentModuleGraph();
    }

    public void setActiveCurve(float x, float y, float toX, float toY, boolean isInput) {
        activeCurve = new Curve(x, y, toX, toY, isInput);
    }

    public void updateActiveCurve(float toX, float toY) {
        if(activeCurve != null) {
            activeCurve.setTo(toX, toY);
        }
    }

    public void addConnectionCurve(ModuleWrapper from, ModuleWrapper to, int slotForm, int slotTo) {
        NodeConnection connection = new NodeConnection();
        connection.fromModule = from;
        connection.toModule = to;
        connection.fromSlot = slotForm;
        connection.toSlot = slotTo;

        getCurrentConnections().add(connection);

        from.setSlotActive(slotForm, false);
        to.setSlotActive(slotTo, true);
    }

    public void makeConnection(ModuleWrapper from, ModuleWrapper to, int slotFrom, int slotTo) {
        mainStage.getCurrentModuleGraph().connectNode(from.getModule(), to.getModule(), slotFrom, slotTo);
        addConnectionCurve(from, to, slotFrom, slotTo);

        from.attachModuleToMyOutput(to, slotFrom, slotTo);
        to.attachModuleToMyInput(from, slotTo, slotFrom);
    }

    public void connectNodeIfCan(ModuleWrapper currentWrapper, int currentSlot, boolean currentIsInput) {
        int[] result = new int[2];
        ModuleWrapper targetWrapper = null;
        boolean targetIsInput = false;
        // iterate over all widgets that are not current and see if mouse is over any of their slots, need to only connect input to output or output to input
        for(ModuleWrapper moduleWrapper: getModuleWrappers()) {
            if(moduleWrapper != currentWrapper) {
                moduleWrapper.findHoveredSlot(result);

                if(result[0] >= 0 ) {
                    // found match
                    targetWrapper = moduleWrapper;
                    if(result[1] == 0) {
                        targetIsInput = true;
                    } else {
                        targetIsInput = false;
                    }
                    break;
                }
            }
        }

        if(targetWrapper == null || currentIsInput == targetIsInput) {
            // removing
        } else {
            // yay we are connecting
            ModuleWrapper fromWrapper, toWrapper;
            int fromSlot, toSlot;

            if(targetIsInput) {
                fromWrapper = currentWrapper;
                toWrapper = targetWrapper;
                fromSlot = currentSlot;
                toSlot = result[0];
            } else {
                fromWrapper = targetWrapper;
                toWrapper = currentWrapper;
                fromSlot = result[0];
                toSlot = currentSlot;
            }

            //what if this already exists?
            if(findConnection(toWrapper, true, toSlot) == null) {
                makeConnection(fromWrapper, toWrapper, fromSlot, toSlot);
            }
        }

        removeActiveCurve();
    }

    public void removeActiveCurve() {
        activeCurve = null;
    }

    public int getUniqueIdForModuleWrapper() {
        int maxId = -1;
        for (ModuleWrapper wrapper: moduleWrappers.get(currentEmitterWrapper)) {
            if(wrapper.getId() > maxId) {
                maxId = wrapper.getId();
            }
        }

        return maxId + 1;
    }


}
