package buzz.getcoco.sample.adapters;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.RecyclerView;
import buzz.getcoco.iot.Capability;
import buzz.getcoco.iot.CapabilityOnOff;
import buzz.getcoco.iot.CapabilityTemperatureSensing;
import buzz.getcoco.iot.Command;
import buzz.getcoco.iot.android.AttributeEx;
import buzz.getcoco.iot.android.ResourceEx;
import com.getcoco.sample.R;
import com.getcoco.sample.databinding.RecyclerItemResourceBinding;
import java.util.ArrayList;
import java.util.List;

public class ResourceTileAdapter extends RecyclerView.Adapter<ResourceTileAdapter.ResourceItemViewHolder> {

  private static final String TAG = "ResourceTileAdapter";

  @NonNull
  private final LifecycleOwner owner;
  private List<ResourceEx> resourceList = new ArrayList<>();

  public ResourceTileAdapter(@NonNull MutableLiveData<List<ResourceEx>> resourcesObservable, @NonNull LifecycleOwner owner) {
    this.owner = owner;

    resourcesObservable.observe(owner, this::setResources);
  }

  private void setResources(List<ResourceEx> resources) {
    Log.d(TAG, "setResources: " + resources);

    this.resourceList = resources;
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public ResourceItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    RecyclerItemResourceBinding binding = RecyclerItemResourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);

    return new ResourceItemViewHolder(binding, owner);
  }

  @Override
  public void onBindViewHolder(@NonNull ResourceItemViewHolder holder, int position) {
    holder.currentResourceObservable.postValue(holder.resource = resourceList.get(position));
  }

  @Override
  public int getItemCount() {
    return resourceList.size();
  }

  protected static class ResourceItemViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "ResourceItemVH";
    private ResourceEx resource;
    private final MutableLiveData<ResourceEx> currentResourceObservable = new MutableLiveData<>();

    public ResourceItemViewHolder(@NonNull RecyclerItemResourceBinding binding, @NonNull LifecycleOwner lifecycleOwner) {
      super(binding.getRoot());

      Transformations
          .switchMap(currentResourceObservable, ResourceEx::getNameObservable)
          .observe(lifecycleOwner, binding.tvResourceName::setText);

      /**
      // for temp attr
      Transformations
          .switchMap(currentResourceObservable, resource -> {
            AttributeEx tempAttr = resource.getAttribute(CapabilityTemperatureSensing.AttributeId.CURRENT_TEMP_CELSIUS);

            if (null != tempAttr)
              return tempAttr.getCurrentValueObservable();
            else
              return new MutableLiveData<>(null);
          })
          .observe(lifecycleOwner, currentValue -> {

            if (currentValue instanceof Integer) {
              int temperature = (int) currentValue;
              binding.tvValue.setText(binding.getRoot().getContext().getString(R.string.num_c, temperature));
              binding.tvDescription.setText(R.string.temperature);
            }

            binding.btOnOff.setVisibility(View.INVISIBLE);
          });

      //for on flag attr
      Transformations
          .switchMap(currentResourceObservable, resource -> {
            AttributeEx attribute = resource.getAttribute(CapabilityOnOff.AttributeId.ON_FLAG);

            if (null != attribute)
              return attribute.getCurrentValueObservable();
            else
              return new MutableLiveData<>(null);
          })
          .observe(lifecycleOwner, currentValue -> {
            int visibility = View.INVISIBLE;
            if (currentValue instanceof Boolean) {
              boolean onFlag = (boolean) currentValue;
              visibility = View.VISIBLE;
              binding.tvDescription.setText(R.string.power_supply);
              binding.tvValue.setText(onFlag ? R.string.on : R.string.off);
              binding.btOnOff.setChecked(onFlag);
            }

            binding.btOnOff.setVisibility(visibility);
      });
       */

      currentResourceObservable.observe(lifecycleOwner, resourceEx -> {
        rebind(resourceEx, binding, lifecycleOwner);
      });

      binding.btOnOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
        CapabilityOnOff capabilityOnOff = resource.getCapability(Capability.CapabilityId.ON_OFF_CONTROL);

        if (null == capabilityOnOff) {
          return;
        }

        Command<CapabilityOnOff.CommandId> command = isChecked ? new CapabilityOnOff.On() : new CapabilityOnOff.Off();

        capabilityOnOff.sendResourceCommand(command, (commandResponse, throwable) -> {
          Log.d(TAG, "ResourceItemViewHolder: response: "
              + commandResponse + ", throwable: "
              + throwable);

          Handler handler = new Handler(Looper.getMainLooper());
          if (null != throwable) {
            handler.post(() -> binding.btOnOff.setChecked(!isChecked));
            return;
          }

          String message = (Command.State.SUCCESS == commandResponse.getState()) ? "Command Success" : "Command Failed";

          handler.post(() -> Toast
              .makeText(binding.getRoot().getContext(), message, Toast.LENGTH_SHORT)
              .show());
        });
      });

      binding.ivBell.setOnClickListener(v -> {
        Toast.makeText(binding.getRoot().getContext(), "TODO Notify", Toast.LENGTH_SHORT).show();
      });
    }

    private void rebind(ResourceEx resource, RecyclerItemResourceBinding binding, LifecycleOwner owner) {
      if (isControlResource(resource)) {
        AttributeEx attribute = resource.getAttribute(CapabilityOnOff.AttributeId.ON_FLAG);
        attribute.getCurrentValueObservable().observe(owner, currentValue -> {
          if (currentValue instanceof Boolean) {
            setControlValues(binding, (Boolean) currentValue);
          }
        });
      }

      if (isTemperatureResource(resource)) {
        AttributeEx attribute = resource.getAttribute(CapabilityTemperatureSensing.AttributeId.CURRENT_TEMP_CELSIUS);
        attribute.getCurrentValueObservable().observe(owner, currentValue -> {
          if (currentValue instanceof Double) {
            setTemperatureValues(binding, (Double) currentValue);
          }
        });
      }
    }
  }

  private static boolean isControlResource(ResourceEx resource) {
    return null != resource.getCapability(Capability.CapabilityId.ON_OFF_CONTROL);
  }

  private static boolean isTemperatureResource(ResourceEx resource) {
    return null != resource.getCapability(Capability.CapabilityId.TEMPERATURE_MEASUREMENT);
  }

  private static void setTemperatureValues(RecyclerItemResourceBinding binding, Double temperature) {
    binding.tvDescription.setText(R.string.temperature);
    binding.tvValue.setText(binding.getRoot().getContext().getString(R.string.num_c, temperature.intValue()));
    binding.btOnOff.setVisibility(View.INVISIBLE);
  }

  private static void setControlValues(RecyclerItemResourceBinding binding, boolean onFlag) {
    binding.tvDescription.setText(R.string.power_supply);
    binding.tvValue.setText(onFlag ? R.string.on : R.string.off);
    binding.btOnOff.setVisibility(View.VISIBLE);
    binding.btOnOff.setChecked(onFlag);
  }

  private static boolean isBoth(ResourceEx resource) {
    return isControlResource(resource) && isTemperatureResource(resource);
  }
}
